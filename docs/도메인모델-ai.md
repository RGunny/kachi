# AI 컨텍스트 도메인 모델

ai-service의 도메인 모델이다. 수집된 뉴스를 키워드별로 LLM 요약하고, 키워드를 확장하며,
window/watermark 기반 실행 모델과 키워드 격리로 운영을 지탱한다.
공통 관례는 [도메인모델.md](도메인모델.md)를 따른다.

관련 결정: ADR 010(초기 설계), 011(newsHash 중복 방지), 020(scheduler 실행 모델과 요약 window), 
  021(LLM 실패 분류와 provider circuit breaker).

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
- `provider`: `LlmProviderName` / `model`: `LlmModelName` / `promptVersion`: `PromptVersion`
- `tokenUsage`: `TokenUsage` 사용 token 수
- `createdAt`: 생성 시각

#### 행위(Behaviors)

- `static create(...)`: 요약을 생성한다. 뉴스 id 중복을 제거하고 제목·본문을 trim한다
- `static restore(...)`: 저장소 snapshot을 복원한다

#### 규칙(Rules)

- 요약 대상 뉴스는 하나 이상이어야 하고, news hash·제목·본문은 빈 값일 수 없다.
- 요약은 어떤 뉴스 묶음에서 생성되었는지 추적할 수 있어야 한다.
- 같은 키워드, news hash, prompt version, model 조합은 중복 저장하지 않는다
  (저장소 unique index로 방어하며, 같은 hash의 기존 요약은 재사용한다 — ADR 011).
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
- 키워드 귀속 실패(`LlmFailure.keywordBound`)만 연속 실패로 센다. 
  인프라 전역 실패는 세지 않는다 (ADR 021).

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
- `PROVIDER_UNAVAILABLE`: 전역 LLM 장애로 이번 실행에서 호출하지 않았다.

### 실패 사유(AiFailureReason)

_Enum_

AI 처리 실패 사유 분류다. 실행 기록과 격리 기록에 남는다.

- `TIMEOUT`, `RATE_LIMITED`, `CLIENT_ERROR`, `SERVER_ERROR`, `NETWORK_ERROR`,
  `INVALID_RESPONSE`, `UNKNOWN`
- `EMPTY_INPUT`: 뉴스 없음은 skip으로 분리되어(ADR 021) 새 실행은 이 값을 쓰지 않는다.
  이전 실행 기록을 읽기 위해 남겨둔 값이다.

## LLM 실패 모델

### LLM 실패(LlmFailure)

_Value Object_

LLM provider 호출 실패를 원천/성격과 함께 보존하는 값이다. 이 값 하나로 세 가지 판단이 갈린다: 
실행 기록에 남길 실패 원인, 키워드 격리 카운트를 올릴 것인가(`keywordBound`),
provider circuit breaker에 실패로 기록할 것인가(`retryable`). (ADR 021)

#### 속성(Attributes)

- `code`: `LlmFailureCode` 표준 실패 코드
- `provider`: `LlmProviderName`
- `message`: 실패 메시지 (기본값 `code.defaultMessage`, 빈 값 불가)
- `statusCode`: 외부 HTTP status code
- `retryAfterMillis`: provider가 알려준 재시도 대기 시간 (음수 불가)
- `source`: `LlmFailureSource` — `code`에서 파생
- `category`: `LlmFailureCategory` — `code`에서 파생

#### 행위(Behaviors)

- `retryable`: 다음 tick이 같은 구간을 다시 처리하면 해소될 수 있는 실패인가.
  category가 `TIMEOUT`, `RATE_LIMITED`, `TRANSIENT_ERROR`일 때 true.
- `keywordBound`: 키워드에 책임을 물을 수 있는 실패인가.
  category가 `INVALID_RESPONSE`, `VALIDATION_ERROR`일 때 true.

#### 규칙(Rules)

- 원천과 분류는 `code`에서 파생한다. 분류 축이 코드와 어긋난 실패를 만들 수 없다.
- `INVALID_RESPONSE`와 `VALIDATION_ERROR`는 provider가 살아 있다는 증거이므로 retryable에 포함하지 않는다.
- 격리는 재시도로 해결되지 않는 실패만 걷어내는 장치이므로, 재시도로 풀릴 실패는 keywordBound가 아니다.
- provider 원문 실패 코드는 우리 분류 체계와 섞지 않는다. 보존이 필요해지면 별도 필드로 추가한다 (보류 — ADR 021).

### LLM 실패 코드(LlmFailureCode)

_Enum_

ai-service가 정의한 표준 LLM 실패 코드다. 각 상수가 `code`, `defaultMessage`, `source`, `category`를 갖는 실패 분류의 유일한 기준이다.

| 코드 | source | category |
| --- | --- | --- |
| `LLM_TIMEOUT` | NETWORK | TIMEOUT |
| `LLM_RATE_LIMITED` | PROVIDER | RATE_LIMITED |
| `LLM_TRANSIENT_ERROR` | PROVIDER | TRANSIENT_ERROR |
| `LLM_NETWORK_ERROR` | NETWORK | TRANSIENT_ERROR |
| `LLM_CLIENT_ERROR` | PROVIDER | VALIDATION_ERROR |
| `LLM_AUTHORIZATION_ERROR` | PROVIDER | AUTHORIZATION_ERROR |
| `LLM_INVALID_RESPONSE` | PROVIDER | INVALID_RESPONSE |
| `LLM_UNKNOWN_ERROR` | PROVIDER | UNKNOWN |

### LLM 실패 분류(LlmFailureCategory)

_Enum_

LLM 실패의 성격이다. LLM은 200 OK를 주면서 JSON 계약을 어기는 실패가 흔하므로 `INVALID_RESPONSE`를 별도로 둔다.

- `TIMEOUT`, `RATE_LIMITED`, `TRANSIENT_ERROR`, `VALIDATION_ERROR`,
  `AUTHORIZATION_ERROR`, `INVALID_RESPONSE`, `UNKNOWN`

### LLM 실패 원천(LlmFailureSource)

_Enum_

LLM 실패가 발생한 원천이다. ai-service의 외부 I/O는 LLM provider와 MongoDB뿐이고 MongoDB 실패는 이 모델을 거치지 않는다.

- `PROVIDER`, `NETWORK`, `APPLICATION`

notification의 `RetryFailure`/`FailureSource`/`FailureCategory`와 어휘를 정렬하되 코드는 공유하지 않는다 (bounded context 독립 — ADR 021).

### 토큰 사용량(TokenUsage)

_Value Object_

- `inputTokens` / `outputTokens`: 각 0 이상
- `totalTokens`: 합계 (파생)

### LLM 이름·버전(LlmProviderName / LlmModelName / PromptVersion)

_Value Object_

- 각각 provider 이름, model 이름, 프롬프트 버전 문자열. `of()`에서 trim, 빈 값 불가.
- 프롬프트 버전은 AI 응답 재현성과 변경 추적을 위해 요약·확장 결과에 함께 저장한다.

## 키워드 확장 애그리거트

### 키워드 확장(KeywordExpansion)

_Aggregate Root_

키워드 하나를 LLM으로 확장한 결과다. (유스케이스는 현재 비활성 — ADR 010 보류 유지)

#### 속성(Attributes)

- `id`: `KeywordExpansionId` 확장 식별자
- `keyword`: `AiKeyword` 원본 키워드
- `expandedKeywords`: `List<ExpandedKeyword>` 확장 키워드 목록
- `provider` / `model` / `promptVersion`: 사용한 LLM 정보
- `createdAt`: 생성 시각

#### 행위(Behaviors)

- `static create(...)`: 확장 결과를 생성한다. 원본과 같은 키워드(대소문자 무시)를 제거하고, 소문자 기준으로 중복을 제거한다
- `static restore(...)`: 저장소 snapshot을 복원한다

#### 규칙(Rules)

- 정규화 후 확장 키워드는 하나 이상이어야 한다.
- 확장 키워드는 원본 키워드와 함께 추적되어야 한다.
- 같은 원본 키워드, prompt version, model 조합은 중복 저장하지 않는다 (저장소 unique 제약).
- LLM 응답이 비어 있거나 파싱할 수 없으면 실패로 처리하고 결과를 저장하지 않는다.

### 확장 식별자(KeywordExpansionId)

_Value Object_

- `value`: 확장 식별 UUID, `newId()` / `of()`

### AI 키워드 / 확장 키워드(AiKeyword / ExpandedKeyword)

_Value Object_

- 원본 키워드 / 확장 키워드. `of()`에서 trim, 빈 값 불가.
