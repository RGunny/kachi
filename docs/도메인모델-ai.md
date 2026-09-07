# AI 컨텍스트 도메인 모델

ai-service의 도메인 모델이다. 수집된 뉴스를 키워드별로 LLM 요약하고, 키워드를 확장하며,
window/watermark 기반 실행 모델과 키워드 격리로 운영을 지탱한다.
공통 관례는 [도메인모델.md](도메인모델.md)를 따른다.

관련 결정: 
  - ADR 010(초기 설계), 011(newsHash 중복 방지), 020(scheduler 실행 모델과 요약 window),
  - 021(LLM 실패 분류·서킷·failover·격리), 030(LLM 호출 단위·실패 분류·실호출 검증).

## 요약 애그리거트

### 뉴스 요약(NewsSummary)

_Aggregate Root_

키워드별 뉴스 묶음을 LLM으로 요약한 결과다.

#### 속성(Attributes)

- `id`: `NewsSummaryId` 요약 식별자
- `keyword`: `AiKeyword` 요약 기준 키워드
- `sourceNewsIds`: `List<UUID>` 요약 대상 뉴스 식별자 목록 (중복 제거)
- `newsHash`: 요약 대상 뉴스 묶음 중복 확인용 hash
- `title` / `content`: 요약 제목·본문 (trim 저장)
- `sentiment`: `NewsSummarySentiment` 감성
- `provider`: `LlmProvider` 응답한 제공자
- `requestedModel`: 요청에 실은 모델 wire id / `model`: 응답이 보고한 모델 (보고가 없으면 요청 모델)
- `promptVersion`: `PromptVersion`
- `tokenUsage`: `TokenUsage` 사용 token 수
- `createdAt`: 생성 시각

#### 행위(Behaviors)

- `static create(...)`: 요약을 생성한다. 뉴스 id 중복을 제거하고 제목·본문을 trim한다
- `static restore(...)`: 저장소 snapshot을 복원한다

#### 규칙(Rules)

- 요약 대상 뉴스는 하나 이상이어야 하고, news hash·제목·본문은 빈 값일 수 없다.
- 요약은 어떤 뉴스 묶음에서 생성되었는지 추적할 수 있어야 한다.
- 같은 키워드, news hash, prompt version 조합은 중복 저장하지 않는다
  (저장소 unique index로 방어하며, 같은 hash의 기존 요약은 재사용한다 — ADR 011). model은 기록 필드다.
- LLM 응답이 비어 있거나 필수 필드를 파싱할 수 없으면 실패로 처리하고 결과를 저장하지 않는다.

### 요약 식별자(NewsSummaryId)

_Value Object_

- `value`: 요약 식별 UUID, `newId()` / `of()`

### 감성(NewsSummarySentiment)

_Enum_

- `POSITIVE`, `NEUTRAL`, `NEGATIVE`, `UNKNOWN`

### 뉴스 해시(NewsHash)

_Domain Service_ (object)

뉴스 요약 입력 묶음을 식별하는 hash를 계산한다.

- `calculate(keyword, sourceNewsIds)`: `"{keyword}|{정렬·중복제거한 뉴스 id들}"`의
  SHA-256 hex 문자열을 반환한다.

#### 규칙(Rules)

- 뉴스 id를 정렬하고 중복 제거하므로 같은 뉴스 묶음은 조회 순서가 달라도 같은 hash가 된다.
- 제목/본문의 유사도를 판단하는 값이 아니다. 같은 키워드·같은 뉴스 id 묶음의 요약인지 판별하는 중복 저장 방지 키다 (ADR 011).
- 뉴스 id 목록은 하나 이상이어야 한다.

## 워터마크

### 요약 워터마크(SummaryWatermark)

_Aggregate Root_

요약 처리를 끝낸 지점이다. 다음 실행은 이 지점에서 window를 시작하므로, 배포·장애로 멈춰 있던 구간도 재기동 후 이어서 처리한다 (ADR 020).

#### 속성(Attributes)

- `targetType`: `AiRunTargetType` 대상 유형 (target당 watermark 1개)
- `position`: 처리를 끝낸 지점 (collector `collectedAt` 축)
- `updatedAt`: 갱신 시각

#### 행위(Behaviors)

- `static initial(targetType, position, updatedAt)`: 최초 watermark를 만든다
- `static restore(...)`: 저장소 snapshot을 복원한다
- `advanceTo(position, updatedAt)`: 처리 지점을 앞으로 옮긴다. 현재 지점보다 이후가 아니면 옮기지 않고 null을 반환한다

#### 규칙(Rules)

- watermark는 앞으로만 움직인다. 뒤로 옮기는 연산은 두지 않으며, 
  이미 처리한 구간을 다시 열 때는 운영자가 저장된 값을 직접 고친다 (ADR 020 — 후진 API를 만들지 않기로 결정).
- `advanceTo`의 비교는 객체가 들고 있는 값 기준이라 여러 인스턴스의 동시 저장 경합은 막지 못한다 (단일 실행 전제 — ADR 020).

### 요약 윈도우(SummaryWindow)

_Value Object_

한 번의 실행이 처리할 뉴스 수집 구간이다. 구간의 축은 collector의 `collectedAt`이다.

#### 속성(Attributes)

- `from` / `to`: 처리 구간
- `skippedFrom`: maxLookback 하한에 걸려 이번 실행이 건너뛴 구간의 시작 (없으면 null)
- `truncated`: 건너뛴 구간 존재 여부 (파생)

#### 행위(Behaviors)

- `static resolve(watermark, now, overlap, maxLookback)`: watermark에서 이어받는 window를 계산한다

```text
to   = now
from = max(watermark - overlap, to - maxLookback)   // to를 넘지 않게 보정
```

#### 규칙(Rules)

- overlap은 watermark에서 뒤로 물러나는 폭으로 늦게 도착한 뉴스를 흡수한다. 
  겹쳐 읽어 생기는 중복 요약은 newsHash 재사용이 막으므로 항상 보수적으로(뒤로) 잡는다.
- 장기 정지 후 한 window가 무한정 커지지 않도록 from에 maxLookback 하한을 둔다.
  하한에 걸려 건너뛴 구간은 `skippedFrom`으로 보존해 로그·경고에 쓴다.
- overlap은 음수일 수 없고 maxLookback은 0보다 커야 한다.
- watermark가 미래에 있어도 빈 구간이 되지 않도록 from은 to를 넘지 못한다.

## 격리

### 키워드 격리(KeywordQuarantine)

_Aggregate Root_

키워드별 연속 실패 누적과 격리 상태다. 반복 실패하는 키워드 하나가 window 전체의 진행을
막지 않도록 그 키워드만 파이프라인에서 걷어낸다. 
notification의 DEAD 처리(ADR 015)와 같은 방식이다 (ADR 020).

#### 속성(Attributes)

- `id`: `KeywordQuarantineId` 격리 기록 식별자
- `targetType`: `AiRunTargetType` / `keyword`: `AiKeyword` (조합이 추적 단위)
- `consecutiveFailures`: 연속 실패 횟수
- `lastFailureReason`: `AiFailureReason?` 마지막 실패 사유
- `status`: `KeywordQuarantineStatus`
- `quarantinedAt` / `releasedAt`: 마지막 격리/해제 시각 (현재 상태는 `status`가 갖는다)
- `updatedAt`: 갱신 시각

#### 행위(Behaviors)

- `static track(targetType, keyword, updatedAt)`: `TRACKING` 상태로 추적을 시작한다
- `static restore(...)`: 저장소 snapshot을 복원한다
- `recordFailure(reason, failureThreshold, updatedAt)`: 실패를 누적하고 임계치 도달 시 격리한다
- `recordSuccess(updatedAt)`: 연속 실패 누적을 0으로 되돌린다
- `release(updatedAt)`: 운영자가 격리를 해제한다
- `needsReset()`: 저장이 필요한 상태인지 판단한다 (실패한 적 없는 키워드까지 매 실행 기록하지 않기 위한 조건)

#### 규칙(Rules)

- 격리 임계치는 1 이상이어야 하고, 연속 실패가 임계치에 도달하면 `QUARANTINED`로 전이한다.
- 임계치는 연속 실패에만 반응한다. 성공 한 번으로 누적이 0이 된다.
- 격리된 키워드는 요약 대상과 watermark 전진 판단에서 제외된다. 
  격리 상태에서 들어온 실패/성공 기록은 무시한다 (격리 시점을 덮지 않는다).
- 격리된 키워드만 해제할 수 있다. 자동 해제는 두지 않고 운영자가 원인 확인 후 해제한다.
- 키워드 탓 실패만 연속 실패로 센다 (ADR 021).
  LLM 실패는 시도한 후보 전부가 입력 탓으로 끝났을 때(`LlmProviderException.allInput`), 도메인 불변식 위반은 항상이다.
  모델·제공자·인프라 탓 실패는 세지 않는다.

### 격리 기록 식별자(KeywordQuarantineId)

_Value Object_

- `value`: 격리 기록 식별 UUID, `newId()` / `of()`

### 격리 상태(KeywordQuarantineStatus)

_Enum_

- `TRACKING`: 연속 실패를 누적 중이지만 아직 임계치에 닿지 않은 상태
- `QUARANTINED`: 임계치에 도달해 요약 대상과 watermark 전진 판단에서 제외된 상태
- `RELEASED`: 운영자가 격리를 해제한 상태

## 실행 기록

### AI 실행(AiRun)

_Aggregate Root_

AI 작업 한 번의 실행 이력이다. watermark는 현재 위치만 갖는 값이라 그것만으로는 왜 거기 있는지 알 수 없다. 
어느 구간을 언제 처리했고 어디서 멈췄는지의 판단 근거를 로그가 아니라 DB에 둔다.

#### 속성(Attributes)

- `id`: `AiRunId` 실행 식별자
- `targetType`: `AiRunTargetType` 실행 대상 유형
- `status`: `AiRunStatus` 실행 상태
- `startedAt` / `finishedAt`: 시작·종료 시각
- `requestedKeywords`: 요청 키워드 수
- `succeededCount` / `failureCount` / `skippedCount`: 성공/실패/건너뜀 처리 건수
- `failureReason`: `AiFailureReason?` 실패 사유 요약
- `skipReason`: `AiSkipReason?` 건너뜀 사유 (skippedCount가 0이면 null)
- `provider` / `model` / `promptVersion`: 실행에 사용한 LLM 정보 (실행 전 실패 시 null)
- `windowFrom` / `windowTo`: 처리한 요약 window 구간 (뉴스 요약 실행에만 채워진다)
- `watermarkAdvanced`: 이 실행이 watermark를 전진시켰는지

#### 행위(Behaviors)

- `static start(targetType, requestedKeywords, startedAt, windowFrom?, windowTo?)`: `RUNNING`으로 실행을 시작한다
- `static restore(...)`: 저장소 snapshot을 복원한다
- `complete(succeededCount, failureCount, failureReason, provider, model, promptVersion, finishedAt, skippedCount, skipReason, watermarkAdvanced)`: 실행을 완료한다

#### 규칙(Rules)

- `RUNNING` 상태의 실행만 완료할 수 있고, 완료 시각은 시작 시각보다 이전일 수 없다.
- 처리 결과는 성공/실패/skip 셋으로 나누며 `requestedKeywords = succeededCount + failureCount + skippedCount`다.
- skip은 조치할 것이 없는 결과라 실패로 세지 않는다. 완료 상태 결정:
  - 실패와 성공이 섞이면 `PARTIALLY_FAILED`
  - 실패만 있으면 `FAILED`
  - 성공 또는 skip이 있으면 `SUCCEEDED` (전부 skip이어도 성공)
  - 전부 0이면 `FAILED` (요청 키워드 0건)
- `SUCCEEDED`가 아니면 실패 사유를 남긴다 (없으면 `UNKNOWN`).
- window 구간이 있으면 시작은 종료보다 이후일 수 없다.
- `AiRun`은 notification/history 장기 이력이 아니라 AI 처리 운영 기록이다.

### 실행 식별자(AiRunId)

_Value Object_

- `value`: 실행 식별 UUID, `newId()` / `of()`

### 실행 상태(AiRunStatus)

_Enum_

- `RUNNING`, `SUCCEEDED`, `PARTIALLY_FAILED`, `FAILED`

### 실행 대상 유형(AiRunTargetType)

_Enum_

- `KEYWORD_EXPANSION`, `NEWS_SUMMARY`

### 건너뜀 사유(AiSkipReason)

_Enum_

키워드를 요약하지 않고 건너뛴 이유 분류다. skip은 실패가 아니다 — 조치할 것이 없는 실행과 원인을 봐야 하는 실행을 실행 기록에서 구분한다 (ADR 021).

- `NO_INPUT`: 이 구간에 요약할 뉴스가 없었다. collector가 정상 응답으로 빈 결과를 준 경우다.
- `PROVIDER_UNAVAILABLE`: 호출할 수 있는 후보 모델이 없어 이번 실행에서 호출하지 않았다.

### 실패 사유(AiFailureReason)

_Enum_

AI 처리 실패 사유 분류다. 실행 기록과 격리 기록에 남는다.

- `TIMEOUT`, `RATE_LIMITED`, `CLIENT_ERROR`, `SERVER_ERROR`, `NETWORK_ERROR`,
  `INVALID_RESPONSE`, `UNKNOWN`
- `PROVIDER_UNAVAILABLE`: 호출할 수 있는 LLM 모델이 없어 요청을 보내지 못했다.
- `MODEL_NOT_FOUND`: 제공이 끝났거나 이름이 틀린 모델을 불렀다.
- `ACCOUNT_ERROR`: 제공자 계정 문제. 인증 실패, 결제 필요, 권한이나 한도.
- `EMPTY_INPUT`: 뉴스 없음은 skip으로 분리되어(ADR 021) 새 실행은 이 값을 쓰지 않는다.
  이전 실행 기록을 읽기 위해 남겨둔 값이다.

## LLM 호출 단위

`domain/llm`의 enum이 어느 규격의 어느 제공자·모델을 어느 용도로 부르는지 정한다. 주소·키·시간·후보 순서만 yaml이다 (ADR 030).

### API 규격(LlmApi)

_Enum_

- `OPENAI_CHAT_COMPLETIONS`
- 요청·응답 JSON의 모양. 코드가 분기하는 유일한 축이며 규격마다 adapter 하나가 있다.

### 제공자(LlmProvider)

_Enum_

- `GROQ`, `MISTRAL`, `OLLAMA`, `OPENROUTER`, `TOGETHER`, `CEREBRAS`, `MOONSHOT`
- 속성: `code`(영속 문서·이벤트·서킷 이름에 쓰는 고정 문자열), `api`
- 한 계정으로 부르는 서비스 하나. 상수명은 회사명이고 과금 상태는 이름에 넣지 않는다.

### 모델(LlmModel)

_Enum_

- `GROQ_QWEN3_27B`, `MISTRAL_SMALL_2603`, `OLLAMA_QWEN3_27B`
- 속성: `provider`, `code`(요청에 싣는 wire id), `options`(`LlmRequestOptions`), `qualifiedCode`(`provider.code/code`, 파생)
- 실호출 검증을 통과한 것만 둔다. 제공이 끝난 모델은 상수를 지운다.
- `-latest` 같은 이동 alias는 쓰지 않는다. 어느 날 다른 모델이 응답해도 알 길이 없다.

### 용도(LlmUse)

_Enum_

- `NEWS_SUMMARY`, `KEYWORD_EXPANSION`
- `LlmProviderPort`의 메서드와 1:1이다. 용도마다 후보 모델의 순서가 따로 있다.

### 과금 방식(LlmBilling)

_Enum_

- `FREE_TIER`, `METERED`, `SUBSCRIPTION`, `SELF_HOSTED`
- 제공자 계정의 속성이라 yaml이 정한다. `SELF_HOSTED`만 인증 키가 없어도 된다.
- `METERED`는 실호출 검증에서 별도 클래스로 opt-in한다.

### 요청 옵션(LlmRequestOptions / ReasoningEffort / Thinking)

_Value Object_

- `reasoningEffort`: `OMIT`, `NONE`, `LOW`, `MEDIUM`, `HIGH`, `MAX`
- `thinking`: `OMIT`, `ENABLED`, `DISABLED`
- `OMIT`은 요청에 그 필드를 싣지 않는다. 모든 모델 상수가 모든 값을 명시하므로 새 값이 생기면 컴파일이 누락을 잡는다.

### 프롬프트(LlmPrompt / NewsSummaryPrompt / KeywordExpansionPrompt)

_Value Object_

- `use`, `version`(`PromptVersion`), `system`, `maxTokens`
- 용도마다 object 하나가 본문과 버전을 한 파일에 둔다. 본문·입력 모양·출력 형식이 바뀔 때만 버전을 올리고 모델 교체는 대상이 아니다 (ADR 030).
- user 메시지는 자연어가 아니라 입력 객체의 JSON이다. system이 그 값을 인용 데이터로 선언해 기사 제목·키워드 속 지시문이 명령으로 읽히지 않게 한다.
- 요약 입력은 `NewsSummaryPrompt.Article`(source·title·publishedAt)이다. domain이 application의 뉴스 모델을 import하지 않기 위해서다.

### 프롬프트 버전(PromptVersion)

_Value Object_

- `of()`에서 trim, 빈 값 불가.
- 요약·확장 저장 키의 일부다. 올리면 같은 입력도 새 프롬프트로 다시 생성되고, 올리지 않으면 옛 결과를 재사용한다.

## LLM 실패 모델

### LLM 실패(LlmFailure)

_Value Object_

LLM 호출 실패 하나를 표준 코드와 함께 보존하는 값이다.
소비처가 내리는 판단마다 파생 프로퍼티가 하나 있고, 판단 축은 모두 `code`에서 파생한다 (ADR 021·030).

#### 속성(Attributes)

- `code`: `LlmFailureCode` 표준 실패 코드
- `provider`: `LlmProvider?` 실패를 낸 제공자. 호출 전에 차단된 실패는 null
- `message`: 실패 메시지 (기본값 `code.defaultMessage`, 빈 값 불가)
- `statusCode`: 외부 HTTP status code
- `retryAfterMillis`: 429의 Retry-After (음수 불가)
- `attribution`: `LlmFailureAttribution` — `code`에서 파생
- `transient`: 다음 tick이나 다음 후보에서 저절로 풀리는가 — `code`에서 파생

#### 행위(Behaviors)

- `fromActualCall`: 실제로 제공자를 호출해서 얻은 실패인가. `attribution != NONE`
- `recordsInCircuit`: 서킷 브레이커에 실패로 기록할 것인가. 실제 호출이고 transient
- `holdsModel`: 이 모델을 한동안 후보에서 뺄 것인가. `MODEL`이고 transient가 아님
- `holdsProvider`: 이 제공자의 모든 모델을 한동안 뺄 것인가. `PROVIDER`이고 transient가 아님

#### 규칙(Rules)

- 두 축은 `code`에서 파생한다. 코드와 어긋난 축을 가진 실패를 만들 수 없다.
- 응답 계약 위반은 모델이 살아 있다는 증거이고, 404·402는 한 건으로 확정이라 서킷의 표본이 아니다.
- 키워드 격리 판단은 여기 없다. 실패 하나가 아니라 시도한 후보 전부의 책임으로 정하므로 `LlmProviderException.allInput`이 맡는다.
- provider 원문 실패 코드는 우리 분류 체계와 섞지 않는다. 보존이 필요해지면 별도 필드로 추가한다.

### LLM 실패 코드(LlmFailureCode)

_Enum_

ai-service가 정의한 표준 LLM 실패 코드다. 각 상수가 `code`, `defaultMessage`, `attribution`, `transient`를 갖는 실패 분류의 유일한 기준이다.

| 코드 | 상황 | attribution | transient |
| --- | --- | --- | --- |
| `LLM_MODEL_NOT_FOUND` | 404 | MODEL | false |
| `LLM_REQUEST_REJECTED` | 400·413·422 | INPUT | false |
| `LLM_UNAUTHORIZED` | 401 | PROVIDER | false |
| `LLM_PAYMENT_REQUIRED` | 402 | PROVIDER | false |
| `LLM_FORBIDDEN` | 403 | PROVIDER | false |
| `LLM_RATE_LIMITED` | 429 | PROVIDER | true |
| `LLM_SERVER_ERROR` | 5xx | PROVIDER | true |
| `LLM_TIMEOUT` | timeout | PROVIDER | true |
| `LLM_NETWORK_ERROR` | 연결·I/O | PROVIDER | true |
| `LLM_INVALID_RESPONSE` | 200이지만 JSON 계약 위반, 빈 content | INPUT | false |
| `LLM_NOT_PERMITTED` | 서킷·cooldown·hold가 호출 전에 막음 | NONE | true |
| `LLM_UNKNOWN_ERROR` | 그 외 | PROVIDER | true |

### LLM 실패 책임(LlmFailureAttribution)

_Enum_

LLM 실패의 책임이 어디 있는가다. 같은 실패라도 책임에 따라 다음 판단이 갈린다.

- `INPUT`: 이 키워드의 요청이나 응답. 다른 모델도 같은 결과를 줄 수 있다.
- `MODEL`: 모델 하나. 그 모델만 빼면 된다.
- `PROVIDER`: 계정과 endpoint. 그 제공자의 모든 모델이 같다.
- `NONE`: 우리 가드가 호출 전에 막았다. 어느 쪽 상태도 말해 주지 않는다.

notification의 `RetryFailure`와 어휘를 맞추되 코드는 공유하지 않는다 (bounded context 독립 — ADR 021).

### 토큰 사용량(TokenUsage)

_Value Object_

- `inputTokens` / `outputTokens`: 각 0 이상
- `totalTokens`: 합계 (파생)

## 키워드 확장 애그리거트

### 키워드 확장(KeywordExpansion)

_Aggregate Root_

키워드 하나를 LLM으로 확장한 결과다. (유스케이스는 현재 비활성 — ADR 010 보류 유지)

#### 속성(Attributes)

- `id`: `KeywordExpansionId` 확장 식별자
- `keyword`: `AiKeyword` 원본 키워드
- `expandedKeywords`: `List<ExpandedKeyword>` 확장 키워드 목록
- `provider`: `LlmProvider` / `requestedModel` / `model` / `promptVersion`: 사용한 LLM 정보 (뉴스 요약과 같다)
- `createdAt`: 생성 시각

#### 행위(Behaviors)

- `static create(...)`: 확장 결과를 생성한다. 원본과 같은 키워드(대소문자 무시)를 제거하고, 소문자 기준으로 중복을 제거한다
- `static restore(...)`: 저장소 snapshot을 복원한다

#### 규칙(Rules)

- 정규화 후 확장 키워드는 하나 이상이어야 한다.
- 확장 키워드는 원본 키워드와 함께 추적되어야 한다.
- 같은 원본 키워드, prompt version 조합은 중복 저장하지 않는다 (저장소 unique 제약 — ADR 011). model은 기록 필드다.
- LLM 응답이 비어 있거나 파싱할 수 없으면 실패로 처리하고 결과를 저장하지 않는다.

### 확장 식별자(KeywordExpansionId)

_Value Object_

- `value`: 확장 식별 UUID, `newId()` / `of()`

### AI 키워드 / 확장 키워드(AiKeyword / ExpandedKeyword)

_Value Object_

- 원본 키워드 / 확장 키워드. `of()`에서 trim, 빈 값 불가.
