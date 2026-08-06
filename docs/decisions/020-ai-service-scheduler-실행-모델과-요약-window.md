# 020. ai-service scheduler 실행 모델과 요약 window

## 배경

`ai-service`는 키워드 확장과 뉴스 요약 유스케이스, 중복 실행을 막는 `AiNewsSummaryExecutor`/`AiKeywordExpansionExecutor`, internal API 진입점까지 구현되어 있었다.

하지만 주기 실행 진입점은 없었다.

- `application.yaml`에 `kachi.ai.scheduler.news-summary` 설정이 있었다.
- `AiServiceApplication`에 `@EnableScheduling`이 있었다.
- 정작 `@Scheduled`를 선언한 scheduler 클래스가 없었다.

즉 설정과 스케줄링 활성화만 있고 실행 주체가 없는 상태였다. `ai-service`는 수동 internal API로만 동작했고, kachi 전체 파이프라인은 사람이 매번 API를 호출해야 이어졌다.

전체 파이프라인을 자동으로 돌리려면 다음이 필요하다.

- 주기 뉴스 요약 실행 진입점
- 주기 키워드 확장 실행 진입점
- 매 실행이 "어느 기간의 뉴스를 요약할 것인가"를 정하는 규칙

앞의 두 개는 `collector-service`에 선례가 있지만, 세 번째는 `ai-service`에만 있는 문제다. `collector-service`는 외부 provider에서 "지금 시점의 최신 뉴스"를 가져오므로 기간을 정할 필요가 없다. 반면 `ai-service`는 이미 저장된 뉴스 중 **어디부터 어디까지**를 하나의 요약 입력으로 묶을지 정해야 한다.

## 결정

### 실행 모델

`collector-service`의 실행 모델(ADR 008)을 그대로 따른다.

```text
AiNewsSummaryScheduler
  -> AiNewsSummaryExecutor
      -> SummarizeNewsUseCase

AiKeywordExpansionScheduler
  -> AiKeywordExpansionExecutor
      -> ExpandKeywordsUseCase
```

scheduler는 `enabled` 확인, 실행 command 구성, 결과 로깅만 담당한다. 중복 실행 방지는 이미 구현된 `Executor`의 JVM lock에 위임하고, scheduler를 위한 별도 lock을 두지 않는다.

중복 요청 처리도 ADR 008과 같다.

- scheduler 실행은 skip하고 로그를 남긴다.
- internal API 실행은 `409 CONFLICT`를 반환한다.

scheduler는 실행 결과 예외를 삼킨다. `@Scheduled` 메서드에서 예외가 전파되면 이후 tick 동작이 불안정해지므로, 실패는 로그로 남기고 다음 tick을 기다린다.

### 중복 실행 방지

중복 실행 방지는 `adapter.in`의 `AiNewsSummaryExecutor`와 `AiKeywordExpansionExecutor`가 담당한다.
scheduler와 internal API 모두 이 `Executor`를 거치므로, 두 진입점이 같은 규칙을 공유한다.

lock은 `AtomicReference`의 CAS 연산으로 구현한다.

```kotlin
private val runningSummary = AtomicReference<RunningAiNewsSummary?>(null)

suspend fun execute(command: SummarizeNewsCommand): AiNewsSummaryExecutionResult {
    val currentSummary = RunningAiNewsSummary(startedAt = Instant.now(clock))

    // 획득: null일 때만 내 값으로 바꾼다. 검사와 획득이 한 원자 연산이다.
    if (!runningSummary.compareAndSet(null, currentSummary)) {
        return AiNewsSummaryExecutionResult.AlreadyRunning(
            runningSummary = runningSummary.get() ?: currentSummary
        )
    }

    return try {
        AiNewsSummaryExecutionResult.Started(summarizeNewsUseCase.summarize(command))
    } finally {
        // 해제: 내가 넣은 값일 때만 지운다.
        runningSummary.compareAndSet(currentSummary, null)
    }
}
```

설계 요점은 네 가지다.

**획득을 CAS로 한다.** `get()`으로 비어 있는지 확인한 뒤 `set()`으로 채우면, 두 요청이 확인과 채움 사이에 끼어들어 둘 다 통과할 수 있다. `compareAndSet(null, current)`는 확인과 채움이 하나의 원자 연산이라 정확히 하나만 성공한다.

**해제도 CAS로 한다.** `set(null)`로 무조건 비우지 않고 `compareAndSet(currentSummary, null)`로 "내가 넣은 그 값일 때만" 비운다. 자기가 획득한 lock만 해제한다는 보장이 생겨, 나중에 lock 해제 시점이 복잡해져도 남의 실행을 실수로 풀어버리지 않는다.

**해제를 `finally`에 둔다.** 유스케이스가 예외로 끝나도 lock이 반드시 풀린다. 이게 없으면 LLM 호출 한 번의 실패로 이후 모든 tick이 영구히 skip된다.

**`AtomicBoolean`이 아니라 `AtomicReference`를 쓴다.** ADR 008의 `collector-service`는 참/거짓만 필요했지만, `ai-service`는 실행 중인 작업의 `startedAt`을 함께 보관한다. 이 값이 있어야 skip 로그와 internal API의 `409 CONFLICT` 응답에 "언제 시작된 작업 때문에 막혔는지"를 담을 수 있다. 운영 중 tick이 계속 skip될 때 이 시각이 유일한 단서가 된다.

`Executor`는 뉴스 요약과 키워드 확장에 각각 하나씩 있고, lock도 서로 독립이다. 두 작업은 입력과 저장 대상이 겹치지 않으므로 동시에 실행되어도 문제가 없고, 24시간 주기의 키워드 확장이 10분 주기의 뉴스 요약을 막아서도 안 된다.

이 lock은 JVM 내부에만 유효하다. `ai-service` 인스턴스를 여러 개 띄우면 인스턴스마다 별도 lock이 생겨 중복 실행을 막지 못한다. `Executor`가 lock 획득/해제 책임을 갖고 있으므로, 그때는 이 클래스만 distributed lock으로 교체하면 되고 유스케이스와 scheduler는 그대로 둔다.

### 요약 window

매 tick의 요약 대상 기간은 저장된 watermark에서 시작한다.

```text
to   = now
from = max(watermark - overlap, to - maxLookback)
```

```yaml
kachi.ai.scheduler.news-summary:
  fixed-delay: 10m
  overlap: 5m
  max-lookback: 6h
```

세 값이 각각 다른 역할을 맡는다.

| 값 | 역할 |
| --- | --- |
| `watermark` | 마지막으로 처리를 끝낸 지점. 서비스가 멈춰 있던 시간을 인식한다 |
| `overlap` | watermark에서 뒤로 물러나는 폭. 늦게 도착한 뉴스를 흡수한다 |
| `maxLookback` | `from`의 하한. 장기 정지 후 한 window가 무한정 커지는 것을 막는다 |

watermark는 실행이 끝난 뒤 조건부로 전진한다.

```text
격리되지 않은 키워드가 모두 성공 -> watermark = window.to
하나라도 실패                -> watermark 유지
```

실패 시 watermark를 유지하면 다음 tick이 같은 구간을 다시 처리한다. 이때 이미 성공한 키워드는 `newsHash`로 기존 요약을 재사용하므로 LLM을 호출하지 않고, 실패했던 키워드만 다시 시도한다. 즉 **재시도가 별도 장치 없이 성립한다.**

이 재사용은 `newsHash`가 조회 구간에 묶여 있지 않아야 성립한다. watermark 방식의 `to`는 항상 `now`라 실행마다 달라지므로, hash에 `from`/`to`가 남아 있으면 기사 묶음이 같아도 매번 다른 hash가 나온다. ADR 011을 개정해 hash 입력을 `keyword + sourceNewsIds`로 줄인 이유가 여기에 있다.

요약 대상이 0개인 실행(활성 키워드가 없거나 전부 격리된 경우)도 실패가 없으므로 전진한다. 처리할 것이 없던 구간을 붙잡고 있으면 window만 계속 커지다 `maxLookback` 하한에 걸린다.

watermark가 없는 최초 기동에서는 `now - overlap`을 시작점으로 삼는다.

`from`이 `maxLookback` 하한에 걸리면 그 사이 구간은 처리되지 않는다. 이 경우 건너뛴 구간을 warn 로그와 `AiRun`에 남겨 조용히 사라지지 않게 한다.

### window를 정하는 주체

watermark는 저장된 상태라 조회에 출력 포트가 필요하다. 진입점(scheduler, internal API)은 `adapter.in`이므로 출력 포트를 직접 호출하지 않는다.

그래서 진입점은 **구간 정책만** 실어 보내고, 실제 계산과 전진은 유스케이스가 맡는다.

```kotlin
sealed interface SummaryWindowRequest {
    // 수동 실행: 지정 구간만 처리하고 watermark를 읽지도 전진시키지도 않는다
    data class Explicit(val from: Instant?, val to: Instant?) : SummaryWindowRequest

    // scheduler 실행: watermark에서 이어받고, 실패가 없으면 전진시킨다
    data class FromWatermark(val overlap: Duration, val maxLookback: Duration) : SummaryWindowRequest
}
```

`overlap`과 `maxLookback`의 설정 소유는 `AiNewsSummarySchedulerProperties`에 남고, 상태 접근은 application 안에 남는다.

부수 효과로 **수동 실행이 watermark를 움직이지 못한다는 규칙이 타입으로 강제된다.** 운영자가 임의 구간을 지정해 재처리했는데 watermark가 그 구간의 끝으로 움직이면, 지정하지 않은 구간까지 처리된 것으로 기록된다. 진입점이 실수로 전진을 요청할 방법 자체를 없앴다.

### 실행 이력 기록

`AiRun`에 이번 실행이 처리한 구간과 watermark 전진 여부를 남긴다.

```text
AiRun
  windowFrom          이번 실행이 처리한 구간의 시작
  windowTo            구간의 끝
  watermarkAdvanced   이번 실행으로 watermark가 전진했는가
```

watermark는 현재 위치만 갖는 값이라, 그것만으로는 "왜 여기 있는지"를 알 수 없다. 실행 이력이 함께 있어야 어느 구간이 언제 처리됐고 어디서 멈췄는지 추적할 수 있다. 판단 근거는 로그가 아니라 DB에 둔다.

### 실패 키워드 격리

키워드별 연속 실패 횟수를 세고, 임계치에 도달하면 그 키워드를 격리한다.

```text
성공 -> consecutiveFailures = 0
실패 -> consecutiveFailures + 1
        임계치(3회) 도달 -> 격리, 이후 요약 대상에서 제외
```

격리 상태는 별도 collection에 남긴다.

```text
ai_keyword_quarantines
  targetType, keyword, consecutiveFailures, lastFailureReason,
  quarantinedAt, releasedAt, status(TRACKING | QUARANTINED | RELEASED)
  unique index: targetType + keyword
```

상태를 셋으로 둔 이유는 연속 실패 횟수가 격리 **이전부터** 실행을 넘겨가며 누적되어야 하기 때문이다. 아직 임계치에 닿지 않은 키워드도 카운터를 들고 있어야 하므로, 격리되지 않은 추적 상태(`TRACKING`)가 격리 상태(`QUARANTINED`)와 별도로 필요하다. `quarantinedAt`/`releasedAt`은 현재 상태가 아니라 마지막 격리/해제 시각이고, 현재 상태의 판단 기준은 `status` 하나다.

실패한 적 없는 키워드는 문서를 만들지 않는다. 실행마다 전 키워드를 기록하면 정상 동작이 쓰기 부하가 된다.

임계치는 scheduler 실행뿐 아니라 internal API 실행에도 함께 적용되므로 scheduler 설정과 분리한다.

```yaml
kachi.ai.quarantine:
  failure-threshold: 3
```

격리된 키워드는 요약 대상에서 빠지고, watermark 전진 판단에서도 제외된다. 해제는 운영자가 결정한다. 자동 해제는 두지 않는다.

격리가 발생하면 관리자에게 알린다. 알림 경로는 notification 연계가 완성된 뒤 연결하고, 그전까지는 DB 기록과 ERROR 로그로 남긴다.

### 실행 주기

| 대상 | fixed-delay | 기본 enabled |
| --- | --- | --- |
| 뉴스 요약 | 10m | true |
| 키워드 확장 | 24h | false |

## 이유

### window를 watermark 기준으로 잡는 이유

요약 대상 기간을 정하는 방식은 세 가지를 검토했다.

**1. 고정 window (`from = now - fixedDelay`)**

tick마다 정확히 이어 붙이는 방식이다.

이론적으로는 빈틈이 없지만 실제로는 누락이 생긴다. `fixedDelay`는 **이전 실행이 끝난 시점부터** 다음 실행까지의 간격이므로, 요약 실행이 3분 걸렸다면 다음 tick은 13분 뒤에 시작한다. 이때 `now - 10m`은 직전 window의 끝보다 3분 뒤가 되고, 그 3분 사이에 수집된 뉴스는 어느 요약에도 들어가지 않는다.

**2. 겹치는 window (`from = now - lookback`, `lookback > fixedDelay`)**

lookback을 실행 주기보다 길게 두어 연속한 tick의 구간을 의도적으로 겹치는 방식이다. 겹침으로 생기는 중복 요약은 ADR 011의 `newsHash` 선조회가 막으므로 비용이 거의 없다.

문제는 이 방식이 흡수할 수 있는 폭이 `lookback - fixedDelay`로 고정된다는 것이다. 기본값이면 20분이고, 그보다 큰 사건에는 무력하다.

| 상황 | 겹치는 window |
| --- | --- |
| 실행이 3분 지연 | 흡수 |
| 배포/재시작으로 30분 이상 정지 | **누락** |
| LLM provider 장애로 30분 이상 연속 실패 | **누락** |
| 특정 키워드만 부분 실패 | 겹침 구간 안에서만 재시도, 이후 **누락** |
| 실행이 lookback보다 오래 걸림 | **누락** |

근본 원인은 `from`이 `now` 기준이라는 데 있다. 절대 시각으로만 계산하면 **진행 상태를 기억하지 못하므로, 시스템이 멈춰 있던 시간을 인식할 방법이 없다.** lookback을 아무리 늘려도 그보다 긴 정지에는 같은 문제가 반복되고, 늘릴수록 한 window의 뉴스 수가 늘어 LLM 입력 토큰이 커진다.

**3. watermark + 선행마진 (선택)**

`from`을 `now`가 아니라 저장된 watermark에서 시작한다.

세 가지 문제를 세 가지 도구가 나눠서 맡는다.

| 문제 | 도구 |
| --- | --- |
| 누락 — 멈춘 시간을 인식하고 이어받기 | watermark |
| 늦게 도착한 데이터 | 선행마진(`overlap`) |
| 중복 — 겹쳐 읽어도 안전 | `newsHash` (ADR 011) |

2번 방식은 watermark 없이 겹침 하나로 누락과 지연을 모두 처리하려 했고, 그래서 흡수 폭 밖의 사건을 놓쳤다.

**`newsHash`가 watermark 운영을 단순하게 만든다.** watermark 관리가 까다로운 이유는 보통 "너무 앞서면 누락, 너무 뒤면 중복"의 균형을 잡아야 하기 때문인데, 여기서는 중복 비용이 거의 0이다. 그래서 **항상 보수적으로(뒤로) 잡으면 되고**, 균형 문제 자체가 사라진다.

부분 실패 시 watermark를 유지하는 규칙도 같은 이유로 성립한다. 다음 tick이 같은 구간을 다시 처리해도 성공분은 `newsHash`로 재사용되므로, 재시도가 추가 비용 없이 이루어진다. 이건 겹치는 window로는 "겹침 폭 안에서 우연히 성공할 때만" 얻는 결과다.

`overlap`을 5분으로 둔 근거는 collector가 `collectedAt` 기준으로 뉴스를 조회하기 때문이다(`NewsPersistenceAdapter`). `collectedAt`은 수집 시점이라 거의 단조 증가하고, 늦게 도착하는 폭은 수집 run의 길이와 저장 지연 정도다. 발행 시각 기준이었다면 며칠 전 기사가 지금 수집될 수 있어 훨씬 큰 마진이 필요했겠지만, 수집 시각 축에서는 5분이면 충분하다.

### window 계산을 유스케이스에 둔 이유

`from`을 저장된 값에서 시작하는 순간, window 계산은 더 이상 시각 산술이 아니라 상태 조회가 된다. 그래서 "누가 그 상태를 읽는가"를 정해야 했다.

**1. scheduler가 watermark를 직접 읽는다**

기존 코드가 window를 계산하던 자리라 변경이 가장 작다. 하지만 `adapter.in`이 출력 포트를 직접 호출하게 되고, 같은 진입점이 둘(scheduler, internal API)이라 watermark 접근 코드가 양쪽에 생긴다. 전진 시점도 진입점마다 따로 판단하게 되어, 지금은 같은 규칙이어도 갈라지기 쉽다.

**2. 정책 값까지 application 설정으로 옮긴다**

`overlap`/`maxLookback`을 application 계층 설정으로 옮기고 command에는 "watermark를 쓸지" 여부만 담는 방식이다. 계층은 깔끔해지지만 실행 주기와 함께 조정해야 하는 값들이 서로 다른 설정 묶음으로 흩어진다. `fixedDelay`가 10분인데 `overlap`이 5분이라는 관계를 한 화면에서 볼 수 없게 된다.

**3. 진입점이 정책만 실어 보내고 유스케이스가 계산한다 (선택)**

설정 소유는 진입점에, 상태 접근은 application에 남는다. 두 진입점이 같은 계산 경로를 공유하므로 규칙이 갈라지지 않는다.

무엇보다 요청 종류가 전진 여부를 함께 결정하게 되어, "수동 실행은 watermark를 움직이지 않는다"가 문서가 아니라 타입으로 남는다. 1번과 2번은 이 규칙을 진입점이 지켜주기를 기대해야 한다.

### 강제 전진 대신 격리를 택한 이유

watermark 방식에는 새 실패 모드가 있다. 특정 키워드가 영구적으로 실패하면 watermark가 전진하지 않고, window가 계속 커지다 `maxLookback` 하한에 걸려 앞쪽부터 잘려나간다.

처음에는 "watermark가 일정 시간 이상 정체하면 강제로 전진시킨다"를 검토했다. 하지만 이건 증상을 미루는 것에 불과하다. 원인을 모른 채 구간을 건너뛰면, 다음 window에 같은 뉴스가 다시 걸려 같은 실패가 반복되고 그때마다 조금씩 구간을 버린다. **자동화가 문제를 조용히 숨기는 방향으로 작동한다.**

문제의 본질은 키워드 하나 때문에 window 전체의 진행을 판단한다는 데 있다. 전부 막히거나(정체) 전부 버리거나(강제 전진) 둘 중 하나가 된다.

대상을 분리하면 둘 다 피할 수 있다. 실패가 반복되는 키워드만 격리하면 나머지는 정상 진행하고, 격리된 키워드는 더 이상 시도하지 않으므로 같은 실패가 반복되지도 않는다. 그래서 강제 전진 자체가 불필요해진다.

이는 notification-service가 이미 쓰는 DEAD/DLT 처리(ADR 015)와 같은 사고방식이다. 처리할 수 없는 대상을 파이프라인에서 걷어내 격리하고, 나머지 흐름은 계속 진행시키며, 격리된 것은 운영자가 판단한다.

격리 해제를 자동으로 하지 않는 이유도 같다. 3회 연속 실패했다는 것은 일시적 오류가 아닐 가능성이 높고, 원인을 모른 채 자동으로 되돌리면 격리가 무의미해진다.

watermark가 격리 이후에도 정체한다면 그건 격리로도 걸러지지 않는 이상 상황이므로, 자동으로 넘기지 않고 드러내는 편이 맞다.

### 키워드 확장 주기를 길게 둔 이유

키워드 확장은 뉴스 요약과 성격이 다르다.

- 입력이 사용자 관심 키워드 자체이고, 시간에 따라 변하지 않는다.
- 결과는 `keyword + promptVersion + model` 기준으로 중복 저장이 차단된다(ADR 010).
- 반면 비용은 키워드 수만큼의 LLM 호출로 매번 동일하게 발생한다.

10분 주기로 실행하면 매번 같은 결과를 얻기 위해 LLM을 호출하게 된다. 24시간 주기는 신규 키워드 등록을 하루 안에 반영하면서 불필요한 호출을 막는 선이다.

기본 `enabled: false`인 이유는 `ai-service`의 알림 파이프라인이 뉴스 요약 결과만 사용하고, 확장 키워드를 소비하는 주체가 아직 없기 때문이다. 소비자가 생기면 켠다.

## 결과

- `ai-service`가 수동 API 없이 주기적으로 뉴스 요약을 생성한다.
- scheduler와 internal API가 `Executor`의 같은 CAS lock을 공유한다. 중복 실행 방지 규칙이 진입점마다 갈리지 않는다.
- 실행 중인 작업의 시작 시각이 skip 로그와 `409 CONFLICT` 응답에 함께 드러난다.
- 배포, 재시작, 장기 장애로 멈춰 있던 구간을 재기동 후 이어서 처리한다.
- 부분 실패한 키워드는 다음 tick이 자동으로 재시도하고, 성공분은 `newsHash`로 재사용해 LLM을 다시 호출하지 않는다.
- 반복 실패하는 키워드가 전체 진행을 막지 않는다.
- 어느 구간을 언제 처리했고 watermark가 어디서 멈췄는지가 `AiRun`과 격리 기록으로 DB에 남는다.
- 처리하지 못한 구간은 `maxLookback` 하한에 걸릴 때 기록과 함께 드러난다.
- 운영자가 구간을 지정해 실행해도 watermark는 움직이지 않는다. 수동 재처리가 주기 실행의 진행 상태를 흔들지 않는다.
- `overlap`이 `maxLookback`보다 길게 설정되면 기동 시점에 실패한다. 매 실행이 잘린 구간을 만드는 설정으로는 뜨지 않는다.
- 이미 지나간 구간을 다시 처리시켜야 할 때(요약 결과가 잘못 생성된 경우 등) watermark를 과거로 되돌리는 것이 문서 한 건 수정으로 끝난다. `_id`가 `targetType`이라 대상 문서가 한 건으로 정해진다.

## 제외한 것

현재는 watermark 정체 시 강제 전진을 두지 않는다.

격리가 반복 실패 키워드를 걷어내므로 정체가 발생하지 않아야 하고, 그럼에도 정체한다면 격리로 걸러지지 않는 이상 상황이라 자동으로 넘기면 안 된다.

현재는 키워드별 watermark를 두지 않는다.

키워드마다 진행 지점을 따로 관리하면 부분 실패를 더 정밀하게 다룰 수 있지만, 관리 대상이 키워드 수만큼 늘고 격리로 이미 같은 문제를 해결한다. 키워드 간 처리 속도 차이가 실제로 문제가 되는 시점에 다시 판단한다.

현재는 격리 자동 해제를 두지 않는다.

TTL 기반 자동 재시도는 원인이 해소되지 않은 상태에서 같은 실패를 반복시킨다. 해제는 운영자가 원인을 확인한 뒤 결정한다.

현재는 격리 해제 API를 두지 않는다.

해제 규칙은 도메인(`KeywordQuarantine.release`)에 있지만 이를 호출하는 진입점은 만들지 않았다. watermark를 과거로 되돌릴 때와 마찬가지로 문서 한 건 수정으로 처리한다. 격리가 실제로 얼마나 자주 발생하고 어떤 판단을 거쳐 풀리는지 보기 전에 운영 API 형태를 정하면, 쓰지 않는 진입점만 남는다. 격리 발생을 관리자 알림으로 받게 되는 시점(C4)에 함께 판단한다.

현재는 scheduler 단위의 distributed lock을 도입하지 않는다. ADR 008과 같은 이유로, 단일 인스턴스 배포 모델에서는 `Executor`의 JVM lock으로 충분하다. 다만 watermark는 인스턴스가 늘어나면 경합 대상이 되므로, 그 시점에 lock과 함께 다시 다룬다.

현재는 collector 수집 완료를 뉴스 요약 실행의 트리거로 삼지 않는다. 두 서비스의 scheduler는 서로를 모른 채 독립적으로 돈다. watermark가 두 scheduler 사이의 타이밍 어긋남도 함께 흡수하므로, 지금 단계에서 이벤트 기반 연결까지 도입할 이유가 없다. 수집과 요약 사이의 지연을 줄여야 하는 요구가 생기면 별도 결정으로 다룬다.
