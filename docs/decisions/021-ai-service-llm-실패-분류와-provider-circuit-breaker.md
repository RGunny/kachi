# 021. ai-service LLM 실패 분류와 provider circuit breaker

## 배경

`ai-service`는 외부 LLM provider를 호출하는 유일한 서비스다. scheduler(ADR 020)가 10분마다 활성 키워드 수만큼 호출을 내보내고, 실패한 키워드는 다음 tick이 같은 구간을 다시 처리해 재시도한다.

그런데 이 경로에는 실패를 구분하는 장치가 없다.

**HTTP 오류가 분류되지 않는다.** `OpenAiLlmProvider.requestChatCompletion()`은 `.retrieve()`에 `onStatus` 처리가 없다. 429든 401이든 503이든 모두 `WebClientResponseException`으로 application 계층까지 새어 나간다. rate limit 응답의 `Retry-After` 헤더는 읽히지도 않는다.

**실행 기록이 실패 원인을 남기지 못한다.** `SummarizeNewsService.failureReasonOf()`는 두 갈래뿐이다.

```kotlin
is IllegalArgumentException -> AiFailureReason.EMPTY_INPUT
else -> AiFailureReason.UNKNOWN
```

`AiFailureReason`에 `TIMEOUT`, `RATE_LIMITED`, `SERVER_ERROR`, `NETWORK_ERROR`가 정의되어 있지만 어떤 실패도 그 값에 도달하지 않는다. `AiRun`에는 사실상 `UNKNOWN`만 쌓인다.

**인프라 장애가 도메인 데이터를 오염시킨다.** 분류가 없으니 `KeywordQuarantine.recordFailure()`가 모든 실패를 키워드 귀속으로 취급한다. provider rate limit이 3틱 이어지면 멀쩡한 키워드가 임계치에 도달해 전부 격리된다. 격리는 자동 해제가 없으므로(ADR 020) 운영자가 손댈 때까지 그 키워드는 영구히 요약되지 않는다. **provider 장애 10분이 키워드 전체를 영구 정지시키는 경로가 열려 있다.**

**뉴스 0건이 실패로 처리된다.** `summarizeCollectedNews()`의 `require(articles.isNotEmpty())`가 실패를 던진다. 그런데 `CollectorServiceNewsReaderAdapter`를 보면 collector의 HTTP 오류, `success=false`, `data=null`, timeout이 모두 `NewsReaderException`으로 먼저 걸러진다. `articles.isEmpty()`가 되는 경로는 **collector가 정상 200으로 빈 배열을 돌려준 경우 하나뿐**이다. 즉 장애가 아니라 "이 구간에 이 키워드로 수집된 뉴스가 없다"는 정상 상태다.

이것이 실패로 집계되면 ADR 020의 watermark 규칙에 걸린다.

```text
활성 키워드 20개 중 1개에 이번 10분간 뉴스가 없음
  -> failureCount = 1
  -> watermark 전진 안 함
  -> window가 tick마다 확대
  -> 3틱 뒤 그 키워드 격리 -> 대상에서 빠짐 -> 그제서야 watermark 전진
```

뉴스가 뜸한 키워드 하나가 전체 진행을 정체시키고, 결국 스스로 격리된다.

**장애 중 호출량이 줄지 않는다.** provider가 rate limit을 걸어도 남은 키워드가 각자 한 번씩 더 429를 받아낸다. 죽은 provider로 나가는 호출을 차단하는 장치도 없다. ADR 018이 "재시도가 장애를 증폭시키는 도구가 된다"고 경고한 상황이 그대로 성립한다.

## 결정

### LLM 실패 모델

`domain/llm`에 `LlmFailure`를 값 객체로 둔다.

```text
LlmFailure
  code              표준 코드(LlmFailureCode) 또는 provider가 내려준 외부 코드
  message           운영자와 로그가 볼 수 있는 실패 메시지
  source            PROVIDER | NETWORK | APPLICATION
  category          TIMEOUT | RATE_LIMITED | TRANSIENT_ERROR | VALIDATION_ERROR
                    | AUTHORIZATION_ERROR | INVALID_RESPONSE | UNKNOWN
  statusCode?       외부 HTTP status가 있을 때 보존
  retryAfterMillis? provider가 Retry-After를 내려줬을 때 보존
  provider          어느 provider에서 난 실패인가
```

notification-core의 `RetryFailure`(ADR 014)와 어휘를 맞추되 코드 의존은 두지 않는다. `notification-core`를 `ai-service`가 참조하면 두 bounded context가 한 모델을 공유하게 된다.

두 모델의 차이는 의도적이다.

| | notification `RetryFailure` | ai `LlmFailure` |
| --- | --- | --- |
| source | VENDOR, BROKER, DATABASE, NETWORK, APPLICATION | PROVIDER, NETWORK, APPLICATION |
| category | ... CONFLICT, PERMANENT_ERROR | ... INVALID_RESPONSE |
| provider 식별 | 없음 (channel로 구분) | `provider` 필드 |

`ai-service`의 외부 I/O는 LLM provider와 MongoDB뿐이고 MongoDB 실패는 이 모델을 거치지 않으므로 `BROKER`/`DATABASE`가 필요 없다. 반대로 LLM은 200 OK를 주면서 JSON 계약을 어기는 실패가 흔해 `INVALID_RESPONSE`가 필요하다. failover(아래)가 provider 단위로 동작하므로 실패가 어느 provider의 것인지도 값에 남아야 한다.

예외는 하나만 둔다.

```kotlin
class LlmProviderException(val failure: LlmFailure, cause: Throwable? = null) : RuntimeException(...)
```

`retryable` 여부는 `LlmFailure.category`에서 파생한다.

```text
retryable  = TIMEOUT, RATE_LIMITED, TRANSIENT_ERROR
keywordBound = INVALID_RESPONSE, VALIDATION_ERROR
```

### provider 예외 매핑

`OpenAiLlmProvider`의 응답 처리를 `.retrieve()`에서 `exchangeToMono`로 바꾼다. 판단 근거는 [왜 exchangeToMono인가](#왜-exchangetomono인가)에 정리한다.

| 조건 | category | source | code |
| --- | --- | --- | --- |
| 429 | `RATE_LIMITED` | PROVIDER | `LLM_RATE_LIMITED` (+ retryAfterMillis) |
| 401, 403 | `AUTHORIZATION_ERROR` | PROVIDER | `LLM_AUTHORIZATION_ERROR` |
| 그 외 4xx | `VALIDATION_ERROR` | PROVIDER | `LLM_CLIENT_ERROR` |
| 5xx | `TRANSIENT_ERROR` | PROVIDER | `LLM_TRANSIENT_ERROR` |
| timeout 계열 | `TIMEOUT` | NETWORK | `LLM_TIMEOUT` |
| 그 외 연결/IO 실패 | `TRANSIENT_ERROR` | NETWORK | `LLM_NETWORK_ERROR` |
| JSON 파싱 실패, 빈 content, 스키마 불일치 | `INVALID_RESPONSE` | PROVIDER | `LLM_INVALID_RESPONSE` |

timeout 판별은 예외 cause 체인을 훑는다. `SocketTimeoutException`, `ConnectTimeoutException`, `ReadTimeoutException`, `WriteTimeoutException`, `TimeoutException` 중 하나가 체인에 있으면 timeout으로 본다. `VendorHttpExceptionClassifier`와 같은 방식이지만 `adapter/out/llm/LlmHttpExceptionClassifier`로 따로 둔다. 컨텍스트 독립을 위해 이 정도 중복은 받아들인다.

401/403을 별도 category로 분리한 이유는 격리 판단 때문이다. API 키 만료는 키워드와 무관한 전역 실패이므로 4xx라도 키워드에 책임을 물으면 안 된다.

### 실패 원인 기록

`SummarizeNewsService`가 `LlmFailure`를 `AiFailureReason`으로 변환해 `AiRun`에 남긴다. `AiFailureReason` enum은 이미 필요한 값을 갖고 있으므로 그대로 쓴다.

| LlmFailure | AiFailureReason |
| --- | --- |
| `TIMEOUT` | `TIMEOUT` |
| `RATE_LIMITED` | `RATE_LIMITED` |
| `TRANSIENT_ERROR` (source=PROVIDER) | `SERVER_ERROR` |
| `TRANSIENT_ERROR` (source=NETWORK) | `NETWORK_ERROR` |
| `VALIDATION_ERROR`, `AUTHORIZATION_ERROR` | `CLIENT_ERROR` |
| `INVALID_RESPONSE` | `INVALID_RESPONSE` |
| `UNKNOWN` | `UNKNOWN` |

LLM 경로 밖의 실패도 함께 정리한다.

| 예외 | AiFailureReason | 키워드 귀속 |
| --- | --- | --- |
| `NewsReaderException`, `KeywordReaderException` | `SERVER_ERROR` | 아니오 |
| `IllegalArgumentException` | `INVALID_RESPONSE` | 예 |
| 그 외 | `UNKNOWN` | 아니오 |

`IllegalArgumentException`은 도메인 불변식 위반이다. `NewsSummary.create`가 거부하는 값은 이 키워드의 입력이나 LLM 응답에서 왔으므로 키워드 귀속으로 본다.
판별할 수 없는 실패(`UNKNOWN`)는 인프라 쪽으로 본다. 잘못 세면 정상 키워드가 영구 격리되고, 놓치면 다음 실행이 다시 시도할 뿐이다.

`AiFailureReason.EMPTY_INPUT`은 더 이상 새로 기록되지 않는다. 요약 대상이 없는 경우를 skip으로 분리했기 때문이다.
다만 enum 값은 지우지 않는다. 이미 저장된 `ai_runs` 문서가 이 값을 갖고 있어 역직렬화가 깨진다.

### 격리 카운트 규칙

`recordKeywordFailure()`는 **키워드 귀속 실패에서만** 호출한다.

```text
키워드 귀속 (INVALID_RESPONSE, VALIDATION_ERROR)
  -> consecutiveFailures++, 임계치 도달 시 격리

인프라 전역 (RATE_LIMITED, TIMEOUT, TRANSIENT_ERROR, AUTHORIZATION_ERROR)
  -> 카운트 갱신 없음. quarantine 문서를 새로 만들지도 않는다. WARN 로그만 남긴다.
```

인프라 전역 실패도 `AiRun`에는 실패로 집계되어 watermark를 붙잡는다. 재시도는 다음 tick이 담당하고, 그때 이미 성공한 키워드는 `newsHash`로 기존 요약을 재사용한다(개정 ADR 011).

성공 시 카운터를 0으로 되돌리는 규칙(`recordSuccess`)은 그대로 둔다.

### skip과 실패의 분리

요약할 입력이 없는 키워드는 실패가 아니라 **skip**으로 집계한다.

```text
articles.isEmpty()  -> SKIPPED (NO_INPUT)
                       실패 아님, 격리 카운트 아님, watermark 전진을 막지 않음
```

`AiRun`에 `skippedCount`와 `skipReason`을 추가한다. 실패가 `failureCount`/`failureReason` 쌍을 갖는 것과 같은 모양이다.

```text
AiRun
  requestedKeywords = succeededCount + failureCount + skippedCount
  skipReason        NO_INPUT | PROVIDER_UNAVAILABLE (skippedCount > 0일 때만)
```

`skipReason`이 없으면 실행 기록에 "16건 건너뜀"만 남아 조치가 필요한지 판단할 수 없다.

실행 상태 결정 규칙도 함께 바뀐다.

```text
failureCount > 0 && succeededCount > 0  -> PARTIALLY_FAILED
failureCount > 0                        -> FAILED
succeededCount > 0 || skippedCount > 0  -> SUCCEEDED
그 외 (요청 키워드가 0건)                 -> FAILED
```

전부 skip으로 끝난 실행은 처리할 것이 없었을 뿐이므로 실패가 아니다.
요청 키워드가 아예 0건인 실행은 기존 동작(`FAILED`)을 유지한다. 키워드 확장 유스케이스가 이 규칙에 의존하고 있어 함께 바꾸지 않는다.

watermark 전진 조건은 `failureCount == 0`을 유지한다. skip은 실패가 아니므로 전진을 막지 않는다.

이 처리가 누락을 만들지 않는 이유는 collector가 `collectedAt`(수집 시각) 기준으로 뉴스를 조회하기 때문이다(ADR 020). 수집 시각은 거의 단조 증가하므로 이미 지나간 구간에 뉴스가 소급 추가되지 않는다. 늦게 수집된 뉴스는 그다음 window에 잡히고, 그 사이 지연은 `overlap`이 흡수한다. 따라서 "이 구간에 이 키워드로 0건"은 확정이며, 전진시켜도 잃는 것이 없다.

### 전역 장애 조기 중단

다음 두 신호를 만나면 이번 tick의 남은 키워드를 **호출 없이** 중단한다.

```text
1. RATE_LIMITED 응답
2. 모든 provider 불능 (전 provider CB OPEN 또는 Retry-After 대기 중)
```

중단된 키워드는 실제로 호출하지 않았으므로 `skippedCount`에 넣고 skip 사유를 `PROVIDER_UNAVAILABLE`로 구분한다.

```text
NO_INPUT            요약할 뉴스가 없음      -> watermark 전진 허용
PROVIDER_UNAVAILABLE 전역 장애로 호출 중단   -> 조기 중단을 유발한 실패 1건이 이미 failureCount에 있어 전진하지 않음
```

두 skip 사유가 watermark에 다르게 작용하는 것이 아니라, **조기 중단은 반드시 실패 1건을 동반하므로** `failureCount > 0`이 자동으로 성립한다. skip 자체는 어느 경우에도 전진 판단에 관여하지 않는다.

`succeededCount`, `failureCount`, `skippedCount`가 분리되어 있으므로 운영자는 "20개 중 3개 성공, 1개 실패, 16개는 rate limit으로 시도조차 안 함"을 실행 기록에서 읽을 수 있다.

### provider circuit breaker

provider마다 `CircuitBreaker` 인스턴스를 둔다. `resilience4j-reactor`가 `resilience4j-circuitbreaker`를 transitive로 포함하므로 의존성 추가는 없다.

```kotlin
CircuitBreakerConfig.custom()
    .recordException { it is LlmProviderException && it.failure.retryable }
```

`INVALID_RESPONSE`와 `VALIDATION_ERROR`로는 CB를 열지 않는다. 그 응답은 provider가 살아 있다는 증거이고, 특정 키워드의 payload 문제로 provider 전체를 차단하면 나머지 키워드까지 막힌다.

`AUTHORIZATION_ERROR`도 기록하지 않는다. 키 만료는 CB의 `waitDurationInOpenState`가 지난다고 해소되지 않으므로 차단이 아니라 운영자 조치가 필요한 실패다.

설정은 yaml로 노출한다.

```yaml
kachi.ai.providers.circuit-breaker:
  failure-rate-threshold: 50
  slow-call-duration-threshold: 8s
  slow-call-rate-threshold: 80
  wait-duration-in-open-state: 60s
  permitted-number-of-calls-in-half-open-state: 2
  sliding-window-size: 10
  minimum-number-of-calls: 5
```

`permittedNumberOfCallsInHalfOpenState`를 2로 둔 이유는 ADR 018의 "half-open은 probe이지 정상 트래픽 복귀가 아니다" 원칙이다. `slowCallDurationThreshold`는 provider `responseTimeout`(10s)보다 짧게 잡아, timeout으로 잡히기 전에 느려짐을 먼저 감지한다.

### provider failover

`RoutingLlmProvider`가 호출 가능한 후보를 순회한다.

```text
후보 = enabled provider 중 CB가 OPEN이 아니고 Retry-After 대기 중이 아닌 것
각 후보 최대 1회 호출
성공 -> 반환
retryable 실패 -> 다음 후보
keyword-bound 실패 -> 즉시 전파 (다른 provider도 같은 payload로 같은 결과)
후보 소진 -> 즉시 실패, tick 재시도에 위임
```

`RATE_LIMITED`의 `Retry-After`는 CB와 별개로 provider별 `nextAvailableAt`에 반영해 그 기간 동안 후보에서 제외한다. CB는 실패 비율로 열리므로 provider가 명시적으로 지시한 대기 시간을 표현하지 못한다.

이 상태는 in-process에 둔다. 설계 원칙상 in-process에 두지 않는 것은 **재시도 루프**이고, 호출 차단은 in-process가 맞는 자리다.

`AGGREGATE` 모드는 기존대로 미구현을 유지한다.

## 이유

### 실패 모델을 공통 모듈로 올리지 않은 이유

`RetryFailure`와 `LlmFailure`의 필드가 겹치므로 공통 모듈로 추출하는 안을 검토했다. 반려한다.

두 모델은 **변화 이유가 다르다.** notification은 vendor가 늘어날 때(Slack, Discord, Telegram, 이후 email/SMS) 확장되고, ai는 provider 계약이 바뀔 때(function calling, structured output, streaming) 확장된다. 공통 모듈은 한쪽의 확장 요구가 다른 쪽 enum에 값을 추가하는 경로를 만든다. `FailureCategory`에 `INVALID_RESPONSE`가 들어가면 notification의 모든 `when` 분기가 그 값을 처리해야 하고, 그 값은 notification에서 절대 발생하지 않는다.

중복 비용은 enum 두 개와 classifier 하나다. bounded context 독립이 그보다 비싸다. 세 번째 컨텍스트가 같은 모델을 요구하고 세 곳의 변화 이유가 실제로 같다고 확인되면 그때 다시 판단한다.

### 예외 계층을 하나로 둔 이유

notification은 `RetryableException`/`NonRetryableException`을 나눴다. Kafka listener가 예외 타입으로 ack/retry를 결정하기 때문이다(ADR 014). 예외 타입이 곧 runtime 제어 신호다.

`ai-service`에는 그 주체가 없다. in-process 재시도가 없고, 재시도 구동은 watermark tick이라는 영속 상태가 담당한다. 예외 타입으로 분기할 곳이 없는데 계층만 두면, 잡는 쪽에서 결국 `failure.category`를 다시 보게 된다. 판단 기준을 한 군데에 둔다.

### 응답 처리에 exchangeToMono를 쓰는 이유

**HTTP 오류를 Mono 체인 안에서 `LlmProviderException`으로 만들기 위해서다.**

circuit breaker는 체인을 통과하는 예외만 본다.
체인이 프레임워크 예외로 끝나면 CB는 그것을 기록하고, 체인 바깥 `catch`에서 우리 예외로 바꿔 던져도 이미 늦다.
아래 설정은 체인 안에서 분류가 끝나 있어야 성립한다.

```kotlin
.recordException { it is LlmProviderException && it.failure.retryable }
```

`exchangeToMono`는 응답을 받은 자리에서 status를 보고 예외를 만들 수 있어 이 조건을 만족한다.

`retrieve()`는 오류를 `WebClientResponseException`으로 던지므로 CB가 429/5xx/4xx를 status로 다시 판단해야 한다.
분류 규칙이 두 벌이 되고, 한쪽만 고치면 격리는 하지 않는데 circuit breaker는 열리는 상태가 된다.
`retrieve()` + `onStatus`는 체인 안에서 분류할 수 있지만 성공 경로와 오류 경로가 갈라진다.

트레이드오프는 body 소비 책임이다.
`exchangeToMono`는 모든 분기에서 body를 소비해야 하며, 소비하지 않는 분기가 생기면 connection이 반환되지 않는다.

### 격리 카운트를 좁힌 이유

격리는 **재시도로 해결되지 않는 실패**를 파이프라인에서 걷어내는 장치다(ADR 020). 판단 기준을 그대로 적용하면 카운트 대상도 정해진다.

rate limit과 timeout은 재시도로 해결된다. 다음 tick이 같은 구간을 다시 처리하고, 그 사이 provider가 회복하면 성공한다. 이 실패를 카운트하면 **격리가 해결책이 아닌 문제에 개입한다.** 게다가 개입 결과가 영구적이다(자동 해제 없음).

`INVALID_RESPONSE`는 다르다. 같은 키워드와 같은 기사 묶음으로 몇 번을 호출해도 LLM이 계약을 어긴 응답을 낼 가능성이 높다. 프롬프트가 그 키워드에서 깨지거나, 기사 본문에 파싱을 무너뜨리는 내용이 있는 경우다. 이건 tick 재시도로 풀리지 않으므로 격리 대상이 맞다.

### 조기 중단을 넣은 이유

rate limit을 만난 시점에 남은 키워드 19개를 계속 호출하면 429를 19번 더 받는다. 얻는 것은 없고 provider 쪽 카운터만 올라간다. ADR 018의 첫 번째 방어선("장애 중 요청 압력을 낮춘다")이 이 상황을 가리킨다.

전 provider 불능도 같이 넣은 이유는, CB가 이미 호출을 차단하더라도 `CallNotPermitted` 처리와 결과 집계가 키워드 수만큼 도는 것이 무의미하기 때문이다. 결과가 확정된 루프는 돌지 않는 편이 낫다.

중단해도 손실이 없는 이유는 watermark가 유지되기 때문이다. 다음 tick이 같은 구간을 다시 처리하고, 이미 성공한 키워드는 `newsHash`로 재사용된다. **중단은 작업을 버리는 것이 아니라 미루는 것이다.**

### Retry operator를 쓰지 않는 이유

`resilience4j-reactor`에는 `RetryOperator`가 있고 붙이기도 쉽다. 쓰지 않는다.

in-process 재시도는 tick 재시도와 겹친다. 두 재시도가 동시에 있으면 실제 호출 배수가 `키워드 수 × in-process 시도 수 × tick 수`로 곱해지고, 어느 층이 몇 번 시도했는지가 기록에서 사라진다. ADR 018이 경고한 "재시도 총량"이 바로 이 곱이다.

더 중요한 것은 실패 기록의 위치다. tick 재시도는 시도 흔적이 `AiRun`과 watermark에 남는다. in-process 재시도는 아무 데도 남지 않는다. **재시도 구동은 영속 상태에 두고, in-process에는 호출 차단만 둔다.**

### skip을 실패에서 분리한 이유

"뉴스 0건"과 "요약 실패"는 운영 조치가 다르다. 앞은 조치할 것이 없고, 뒤는 원인을 봐야 한다. 한 카운터에 합치면 `AiRun`을 보고 조치가 필요한 실행인지 판단할 수 없다.

`failureCount` 하나로 watermark 전진을 판단하는 ADR 020 규칙도 이 분리에 의존한다. 뉴스가 뜸한 키워드가 섞여 있는 것만으로 전진이 막히면, watermark가 사실상 영구 정체하고 window가 `maxLookback` 하한까지 커져 구간이 잘려나간다. **집계를 나누지 않으면 전진 규칙을 고쳐야 하는데, 규칙은 맞고 입력이 틀렸다.**

## 결과

- provider rate limit 3틱으로 정상 키워드가 영구 격리되는 결함이 사라진다.
- 뉴스가 뜸한 키워드 하나가 watermark 전체를 정체시키고 스스로 격리되는 결함이 사라진다.
- `AiRun.failureReason`이 TIMEOUT / RATE_LIMITED / SERVER_ERROR / NETWORK_ERROR / CLIENT_ERROR / INVALID_RESPONSE를 실제로 구분해 기록한다. 격리된 키워드의 `lastFailureReason`도 원인을 가리킨다.
- `AiRun`에 성공/실패/skip이 분리되어, 실행 하나를 보고 조치가 필요한지 판단할 수 있다.
- rate limit이나 전 provider 장애 시 이번 tick의 호출이 즉시 멈춘다.
- provider 하나가 죽어도 나머지 provider로 요약이 계속된다.
- 죽은 provider로 나가는 호출이 CB로 차단되고, `Retry-After`를 준 provider는 그 기간 동안 후보에서 빠진다.

## 트레이드오프

**failover가 요약 선조회를 빗나갈 수 있다.** `prepareNewsSummary()`가 반환하는 `plan.model`이 요약 선조회 키에 들어간다(ADR 011). failover로 다른 provider가 응답하면 저장되는 `model`이 plan과 달라져, 다음 tick에서 같은 기사 묶음이 선조회를 통과하지 못하고 LLM을 다시 호출할 수 있다.

failover는 장애 시에만 발생하고 중복 호출 비용은 요약 1건이므로 감수한다. 선조회 키에서 `model`을 빼는 안은 ADR 011 계약 변경이라 이번 범위에서 다루지 않는다.

**CB 상태가 인스턴스마다 독립이다.** `ai-service`를 여러 개 띄우면 각 인스턴스가 자기 CB를 갖는다. ADR 018이 지적한 "각 호출자의 half-open probe가 동시에 몰리는" 상황이 성립한다. 단일 인스턴스 배포 모델(ADR 020)에서는 문제가 없고, 인스턴스가 늘어나면 distributed lock과 함께 다시 다룬다.

## 제외한 것

현재는 retry budget을 두지 않는다.

ADR 018의 방어선 4번이지만, budget은 "전체 요청 대비 재시도 비율"로 정의된다. `ai-service`의 재시도는 tick 단위라 분모가 되는 요청량이 10분에 키워드 수만큼으로 고정되어 있고, CB와 조기 중단이 이미 총량을 잡는다. 관측 지표(ADR 019 대응) 없이 비율을 정하면 근거 없는 숫자가 된다.

현재는 bulkhead와 slow start를 두지 않는다.

provider 호출은 scheduler tick 하나에서 순차로 나가므로 동시성 자체가 1이다. 격리할 pool이 없다. slow start도 마찬가지로, CLOSED 복귀 시점에 몰릴 대기 트래픽이 없다. 키워드 병렬 호출을 도입하면 그때 함께 판단한다.

현재는 token/cost budget을 두지 않는다.

ADR 018 후속 7번이다. 비용 상한은 실제 사용량 데이터가 있어야 정할 수 있고, `AiRun`에 `TokenUsage`가 쌓이는 중이다. 관측이 붙는 시점에 다룬다.

현재는 `AGGREGATE` 모드를 구현하지 않는다. ADR 010의 보류를 유지한다.

현재는 provider별 실패율/CB 상태 metric을 노출하지 않는다. observability는 notification 연결 이후로 미룬다.
