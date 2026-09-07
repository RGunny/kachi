# 010. ai-service 초기 설계

## 배경

Kachi의 현재 구현은 다음 상태다.

- `user-service`: 사용자, 인증, 관심 키워드, 활성 키워드 internal API 구현
- `collector-service`: 활성 키워드 기반 뉴스 수집, MongoDB 저장, 수집 실행 기록 구현
- `ai-service`: 미구현

전체 서비스 흐름에서 `ai-service`는 두 위치에 놓인다.

```text
user-service
  -> ai-service keyword expansion
      -> collector-service collection
          -> ai-service news summary
              -> notification-service
```

현재 `user-service`의 활성 키워드 internal API는 keyword 이름만 제공한다.
서비스에 user 가 묶이면 복잡도가 올라가니, 초기 설계에서는 키워드만 받아 ai-service 비즈니스 개발에 집중한다.
여러 유저의 동일 키워드 중복 등 중복제거 효율을 위해 ai-service 에서는 user를 제외하고 개발하는 것이 초기 설계에 맞아보인다.

## 결정

초기 `ai-service`는 키워드 단위 AI 처리 서비스로 설계한다.

핵심 유스케이스는 다음 두 가지다.

- `ExpandKeywordsUseCase`: 원본 키워드 목록을 LLM으로 확장한다.
- `SummarizeNewsUseCase`: 키워드별 뉴스 묶음을 LLM으로 요약한다.

실행 기록은 `AiRun` aggregate로 분리한다.
키워드 확장 결과와 뉴스 요약 결과는 각각 `KeywordExpansion`, `NewsSummary` aggregate로 저장한다.

```text
Internal API 또는 Scheduler
  -> AiExecutionExecutor
      -> ExpandKeywordsUseCase / SummarizeNewsUseCase
          -> KeywordReaderPort
          -> NewsReaderPort
          -> LlmProviderPort
          -> PersistencePort
```

`ai-service`는 Kotlin, Spring WebFlux, reactive MongoDB를 사용한다.
이는 `collector-service`와 같은 성격의 외부 I/O 중심 서비스이고, LLM/provider 호출이 blocking 병목이 되지 않게 하기 위해서다.

## 도메인 경계

### ai-service가 소유하는 것

- 키워드 확장 결과
- 뉴스 요약 결과
- AI 실행 기록
- LLM provider/model/prompt version/token usage 메타데이터
- LLM 응답 파싱과 검증 규칙

### ai-service가 소유하지 않는 것

- 사용자의 관심 키워드 원본 저장
- 수집된 뉴스 원본 저장
- 알림 발송
- 사용자별 장기 이력

다른 서비스의 DB를 직접 읽지 않는다.
필요한 데이터는 internal API 또는 이후 메시지 계약으로 받는다.

## 외부 계약

### user-service

활성 키워드를 읽는다.

```text
GET /api/v1/internal/keywords/active
```

초기 계약은 keyword 이름만 포함하므로 AI 처리도 keyword 이름 단위로 수행한다.

### collector-service

요약 대상 뉴스를 읽기 위한 internal API를 사용한다.

```text
GET /api/v1/internal/news?keyword={keyword}&from={from}&to={to}&limit={limit}
```

응답은 AI 요약 입력에 필요한 최소 필드만 제공한다.

- `id`
- `source`
- `title`
- `url`
- `publishedAt`
- `collectedAt`
- `matchedKeywords`

## 실행 모델

`collector-service`의 실행 모델과 동일하게 scheduler와 internal API를 둘 다 지원한다.

- scheduler: 주기적 뉴스 요약 생성
- internal API: 수동 키워드 확장 또는 수동 뉴스 요약

중복 실행 방지는 application service가 아니라 `adapter.in`의 `AiExecutionExecutor`가 담당한다.
초기에는 단일 인스턴스를 가정하고 JVM 내부 lock을 사용한다.
분산 배포가 필요해지는 시점에 Redis 또는 MongoDB 기반 distributed lock으로 교체한다.

## LLM provider 정책

LLM 연동은 `LlmProviderPort` 뒤에 둔다. application 서비스는 포트만 부르고 어느 제공자의 어느 모델이 응답하는지 모른다.

```text
SummarizeNewsService / ExpandKeywordsService
  -> LlmProviderPort
      -> 용도별 후보 모델 (API 규격별 adapter)
```

호출 단위(API 규격·제공자·모델·용도), yaml에 두는 값, 조립, 실패 분류, 실호출 검증은 ADR 030이 정한다.
실패를 소비처가 어떻게 쓰는지(서킷·hold·격리·조기 중단)는 ADR 021이다.

- 후보 제공자의 credential이 없으면 기동에 실패한다. 후보가 아닌 제공자의 credential은 요구하지 않는다.
- `src/test`는 실제 LLM API를 호출하지 않고 stub 서버로 요청·응답 변환을 검증한다. 실제 호출은 `src/realTest`다.

LLM 응답은 adapter에서 raw response DTO로 받고, application service에서 도메인 결과로 변환하기 전에 파싱 실패와 필수값 누락을 실패로 분류한다.

## 저장 정책

MongoDB collection은 다음을 사용한다.

- `keyword_expansions`
- `news_summaries`
- `ai_runs`

요약 중복 방지를 위해 `news_summaries`에는 다음 기준의 unique index를 둔다.

```text
keyword + newsHash + promptVersion + model
```

`newsHash`는 요약 대상 뉴스 id 목록, 기간, 키워드를 기준으로 계산한다.
같은 키워드라도 뉴스 묶음이나 prompt/model이 바뀌면 새 요약으로 저장할 수 있게 한다.

키워드 확장 중복 방지를 위해 `keyword_expansions`에는 다음 기준의 unique index를 둔다.

```text
keyword + promptVersion + model
```

## 실패 처리

AI 작업은 provider 실패 하나로 애플리케이션 전체를 중단하지 않는다.
키워드별 실패는 `AiRun`의 실패 결과로 기록한다.

실패 사유는 초기에는 다음 수준으로 분류한다.

- `TIMEOUT`
- `RATE_LIMITED`
- `CLIENT_ERROR`
- `SERVER_ERROR`
- `NETWORK_ERROR`
- `INVALID_RESPONSE`
- `EMPTY_INPUT`
- `UNKNOWN`

retry, backoff, 서킷 브레이커, token budget 최적화는 전체 파이프라인 구성 이후 고도화한다.

## 결과

- `ai-service`는 user/collector DB에 직접 접근하지 않고 명시적 internal API 계약으로 연결된다.
- 초기 구현은 현재 keyword-only 계약에 맞춰 키워드 단위로 동작한다.
- 사용자별 개인화는 나중에 user-service internal API가 user id 또는 keyword id를 제공할 때 확장한다.
- 실행 기록과 결과 저장이 분리되어 실패 분석, 재시도, notification 연계가 쉬워진다.
- collector-service와 같은 실행 모델을 사용해 코드 구조와 테스트 전략을 재사용할 수 있다.

## 제외한 것

현재는 Kafka 이벤트 기반 처리를 도입하지 않는다.
서비스 파이프라인의 기본 API 계약과 저장 모델을 먼저 완성한 뒤, 이벤트 기반 비동기화가 필요한 지점을 별도 결정으로 다룬다.

현재는 embedding/vector search를 도입하지 않는다.
초기 요약은 기간과 키워드로 조회한 뉴스 묶음을 대상으로 생성한다.

현재는 프롬프트 전문을 장기 저장하지 않는다.
대신 prompt version, model, provider, token usage를 저장한다.
프롬프트 감사나 재현성이 필요해지는 시점에 별도 보관 정책을 정한다.
