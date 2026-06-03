# ai-service

LLM 기반 키워드 확장과 뉴스 요약을 담당하는 서비스다.

초기 구현 범위는 다음 두 흐름이다.

- 활성 키워드 또는 요청 키워드 기준 관련 검색어를 생성하는 키워드 확장
- `collector-service`가 저장한 뉴스를 읽어 키워드별 뉴스 묶음을 요약하는 뉴스 요약

## 설계 기준

`ai-service`는 기존 서비스와 동일하게 Hexagonal Architecture를 따른다.

```text
Scheduler 또는 Internal API
  -> AiExecutionExecutor
      -> ExpandKeywordsUseCase / SummarizeNewsUseCase
          -> KeywordReaderPort
              -> user-service internal active keywords API
          -> NewsReaderPort
              -> collector-service internal news API
          -> LlmProviderPort
              -> OpenAI 계열 LLM provider
          -> KeywordExpansionPersistencePort / NewsSummaryPersistencePort / AiRunPersistencePort
              -> MongoDB
```

초기에는 `collector-service`와 같은 WebFlux + reactive MongoDB 구성을 사용한다.
LLM 호출은 외부 I/O가 많고 timeout, retry, rate limit 대응이 필요하므로 MVC보다 WebFlux 구성이 더 적합하다.

## 현재 구현 예정 범위

- `KeywordExpansion`, `NewsSummary`, `AiRun` 도메인 모델
- 키워드 확장 internal API
- 뉴스 요약 internal API
- scheduler 기반 뉴스 요약 진입점
- 단일 인스턴스 기준 중복 실행 방지
- MongoDB 기반 확장 결과, 요약 결과, 실행 기록 저장
- LLM provider adapter
- user-service 활성 키워드 조회 client
- collector-service 뉴스 조회 client

## 제외 범위

- 사용자별 개인화 프롬프트
- 장기 이력 조회
- 알림 발송
- embedding/vector search
- 분산 lock
- provider별 상세 비용 최적화

현재 `user-service`의 활성 키워드 internal API는 keyword 이름만 제공한다.
따라서 초기 `ai-service`는 사용자별 확장이 아니라 키워드 단위 확장과 키워드 단위 뉴스 요약으로 설계한다.
사용자별 알림 대상 매핑은 notification/history 단계에서 별도 계약으로 연결한다.

## API

현재 HTTP API는 `/api/v1` prefix를 사용한다.

### Internal

LLM provider 연결 확인:

```http
GET /api/v1/internal/providers/llm/health?keyword=NVIDIA
```

이 API는 현재 provider mode에 따라 키워드 확장 요청을 실제 호출하고 응답 파싱까지 확인한다.
초기 구현은 OpenAI 계열 chat completions provider를 지원한다.

지원 provider:

- `openrouter`
- `groq`
- `together`
- `cerebras`
- `mistral`

`gemini`는 설정 항목은 있지만 별도 `generateContent` adapter 구현 전까지 `enabled=true`로 사용할 수 없다.

local에서 확인하려면 `.env.local`에 사용할 provider를 `enabled=true`로 두고 API key와 model을 지정한다.

```env
KACHI_AI_LLM_PROVIDER_MODE=single-random
KACHI_AI_OPENROUTER_ENABLED=true
OPENROUTER_API_KEY=...
KACHI_AI_OPENROUTER_MODEL=openai/gpt-4o-mini
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "provider": "openrouter",
    "model": "openai/gpt-4o-mini",
    "promptVersion": "keyword-expansion-v1",
    "expandedKeywords": ["AI 반도체", "GPU", "데이터센터"],
    "inputTokens": 100,
    "outputTokens": 20
  },
  "error": null
}
```

키워드 확장 수동 실행:

```http
POST /api/v1/internal/ai/keyword-expansions
```

요청 body를 생략하거나 `keywords`가 비어 있으면 `user-service`의 활성 키워드를 조회한다.

```json
{
  "keywords": ["NVIDIA", "AI 반도체"],
  "maxExpansionsPerKeyword": 5
}
```

뉴스 요약 수동 실행:

```http
POST /api/v1/internal/ai/news-summaries
```

요청 body를 생략하거나 `keywords`가 비어 있으면 `user-service`의 활성 키워드를 조회한다.
`from`과 `to`가 없으면 서비스 기본 lookback 기간을 사용한다.

```json
{
  "keywords": ["NVIDIA"],
  "from": "2026-06-01T00:00:00Z",
  "to": "2026-06-02T00:00:00Z",
  "maxArticlesPerKeyword": 20
}
```

정상 응답:

```json
{
  "success": true,
  "data": {
    "id": "018f...",
    "targetType": "NEWS_SUMMARY",
    "status": "SUCCEEDED",
    "startedAt": "2026-06-02T00:00:00Z",
    "finishedAt": "2026-06-02T00:00:04Z",
    "requestedKeywords": 1,
    "succeededCount": 1,
    "failureCount": 0,
    "failureReason": null
  },
  "error": null
}
```

이미 같은 AI 작업이 실행 중이면 `409 CONFLICT`를 반환한다.

## 저장소

`ai-service`는 MongoDB를 사용한다.

저장 대상:

- `keyword_expansions`: 원본 키워드별 확장 키워드 결과
- `news_summaries`: 키워드별 뉴스 요약 결과
- `ai_runs`: 키워드 확장 또는 뉴스 요약 실행 기록

MongoDB 문서에는 LLM provider, model, prompt version, token usage, 생성 시각을 남긴다.
프롬프트 전문은 운영상 필요해질 때 별도 보관 정책을 정하고 추가한다.

## 외부 연동

### user-service

활성 키워드 조회:

```text
GET {KACHI_USER_SERVICE_BASE_URL}/api/v1/internal/keywords/active
```

### collector-service

요약 대상 뉴스 조회:

```text
GET {KACHI_COLLECTOR_SERVICE_BASE_URL}/api/v1/internal/news?keyword={keyword}&from={from}&to={to}&limit={limit}
```

응답은 AI 요약에 필요한 최소 필드만 포함한다.

- `id`
- `source`
- `title`
- `url`
- `publishedAt`
- `collectedAt`
- `matchedKeywords`

## 실행

예정 local 기본값:

```text
server.port=8083
MONGO_HOST=localhost
MONGO_PORT=27017
MONGO_DATABASE=kachi_ai
KACHI_USER_SERVICE_BASE_URL=http://localhost:8080
KACHI_COLLECTOR_SERVICE_BASE_URL=http://localhost:8082
KACHI_AI_NEWS_SUMMARY_SCHEDULER_ENABLED=false
```

테스트:

```sh
./gradlew :ai-service:test
```

## 관련 문서

- [아키텍처](../docs/아키텍처.md)
- [도메인 모델](../docs/도메인모델.md)
- [테스트 전략](../docs/테스트전략.md)
- [010. ai-service 초기 설계](../docs/decisions/010-ai-service-초기-설계.md)
