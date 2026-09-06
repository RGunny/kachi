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

## Scheduler

스케줄러 실행 진입점은 두 가지다.

| 대상 | 설정 prefix | 기본 주기 | 기본 활성화 |
| --- | --- | --- | --- |
| 뉴스 요약 | `kachi.ai.scheduler.news-summary` | 10m | `true` |
| 키워드 확장 | `kachi.ai.scheduler.keyword-expansion` | 24h | `false` |

뉴스 요약 scheduler는 매 실행마다 `now - lookback ~ now` 기간의 뉴스를 요약 대상으로 삼는다.
`lookback`은 실행 주기보다 길게 두어 tick 사이에 수집된 뉴스가 누락되지 않게 하고,
겹침으로 생기는 중복 요약은 `newsHash` 재사용이 막는다.

local 프로필은 둘 다 켠다. 뉴스 요약은 기본값 그대로이고 키워드 확장은 local에서만 켠다. test 프로필은 둘 다 끈다.

실행 중인 같은 AI 작업이 있으면 이번 tick은 건너뛰고 로그만 남긴다.

## 이벤트 발행

요약 생성과 키워드 격리는 outbox에 기록되고, relay가 그 행을 Kafka topic으로 내보낸다(ADR 022, 023).
outbox 행 하나가 레코드 하나다. topic은 행의 eventType이 고르고, key는 keyword, value는 기록 시점의 계약 JSON이다.
계약 타입은 `ai-contract` 모듈에 있다.

| eventType | topic | 계약 |
| --- | --- | --- |
| `SUMMARY_CREATED` | `ai.summary.created` | `AiSummaryCreatedEvent` |
| `KEYWORD_QUARANTINED` | `ai.keyword.quarantined` | `AiKeywordQuarantinedEvent` |

스위치는 둘이고 뜻이 다르다.

| 키 | 뜻 | 기본값 |
| --- | --- | --- |
| `kachi.ai.outbox.relay.enabled` | outbox를 읽어 발행 포트로 넘기는 relay | `true` |
| `kachi.ai.events.enabled` | Kafka 발행 어댑터 | `true` |

relay만 켜고 어댑터가 없으면 기동에 실패한다. local·test 프로파일은 둘 다 `false`다.
누가 어떤 채널로 받는지는 이 서비스가 모른다. 요약을 알림으로 fan-out하는 일은 notification-service의 routing이 한다(ADR 025).

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

LLM 모델 상태 조회:

```http
GET /api/v1/internal/llm/models
```

어느 용도에든 후보로 오른 모델 전부의 차단 상태를 돌려준다.
서킷 상태와 failure rate·slow call rate, cooldown 종료 시각, hold 사유와 해제 시각, 제공자 계정의 과금 방식이 들어 있다.
`model`은 `LlmModel` 상수명이고 `provider`는 제공자 code다.
제공자·모델·용도의 정의는 `domain/llm`의 enum에 있고, 어느 모델을 쓰는지는 `application.yaml`의 `kachi.ai.llm.uses.*.candidates`다.

```json
{
  "success": true,
  "data": [
    {
      "model": "OLLAMA_QWEN3_27B",
      "provider": "ollama",
      "billing": "SELF_HOSTED",
      "circuitBreakerState": "CLOSED",
      "cooldownUntil": null,
      "holdReason": null,
      "holdUntil": null,
      "failureRate": -1.0,
      "slowCallRate": -1.0,
      "bufferedCalls": 0,
      "successfulCalls": 0,
      "failedCalls": 0,
      "notPermittedCalls": 0
    }
  ],
  "error": null
}
```

차단 해제:

```http
POST /api/v1/internal/llm/models/{model}/reset
```

그 모델의 서킷·cooldown·모델 hold를 풀고 제공자 hold도 함께 푼다. 운영자가 원인 해소를 확인했다는 뜻이다.
응답은 푼 뒤의 상태다.

실제 호출 확인(probe):

```http
POST /api/v1/internal/llm/models/{model}/probe
```

지정한 모델 하나에 키워드 확장 요청을 한 번 실제로 보내 응답 여부를 확인한다. 후보 순회를 거치지 않지만 차단 장치는 그대로 지난다.
차단 중이면 사유를 실어 502로 실패하고, 실패의 결과는 평소 호출과 같이 상태에 남는다.
`billing`이 `METERED`면 과금된 호출이다.

```json
{
  "success": true,
  "data": {
    "model": "GROQ_QWEN3_27B",
    "provider": "groq",
    "billing": "FREE_TIER",
    "requestedModel": "qwen/qwen3.8-27b",
    "servedModel": "qwen/qwen3.8-27b",
    "promptVersion": "keyword-expansion-v2",
    "latencyMillis": 812,
    "inputTokens": 100,
    "outputTokens": 20,
    "expandedKeywords": ["AI 반도체", "GPU", "데이터센터"]
  },
  "error": null
}
```

`{model}`이 상수명이 아니면 400, 상수지만 어느 용도의 후보도 아니면 404다.

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
    "summaries": [
      {
        "id": "018f...",
        "keyword": "NVIDIA",
        "title": "엔비디아 관련 뉴스 요약",
        "sentiment": "NEUTRAL",
        "reused": false
      }
    ]
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

local 기본값:

```text
server.port=8083
MONGO_HOST=localhost
MONGO_PORT=27017
MONGO_DATABASE=kachi_ai
KACHI_USER_SERVICE_BASE_URL=http://localhost:8080
KACHI_COLLECTOR_SERVICE_BASE_URL=http://localhost:8082
kachi.ai.scheduler.news-summary.enabled=false
kachi.ai.scheduler.keyword-expansion.enabled=false
```

실행:

```sh
set -a
source ../.env.local
set +a
./gradlew :ai-service:bootRun
```

`.env.local`의 `SPRING_PROFILES_ACTIVE`가 어느 프로필로 뜰지 정한다.

scheduler·outbox relay·이벤트 발행은 local에서도 켜져 있다. 끄고 띄우려면 실행 인자로 준다.

```sh
./gradlew :ai-service:bootRun --args='--kachi.ai.scheduler.news-summary.enabled=false'
```

테스트:

```sh
./gradlew :ai-service:test
```

Testcontainers 통합 테스트는 Docker 소켓을 찾지 못하면 실패한다.
Docker Desktop을 쓰면 소켓 경로를 함께 넘긴다.

```sh
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew :ai-service:test
```

## 관련 문서

- [아키텍처](../docs/아키텍처.md)
- [도메인 모델](../docs/도메인모델.md)
- [테스트 전략](../docs/테스트전략.md)
- [010. ai-service 초기 설계](../docs/decisions/010-ai-service-초기-설계.md)
- [020. ai-service scheduler 실행 모델과 요약 window](../docs/decisions/020-ai-service-scheduler-실행-모델과-요약-window.md)
