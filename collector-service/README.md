# collector-service

뉴스와 시장 데이터를 외부 provider에서 수집하고, 수집 실행 기록과 신규 뉴스를 MongoDB에 저장하는 서비스다.

현재 구현 범위는 뉴스 수집이다. 시장 데이터 수집은 아직 구현하지 않았다.

## 현재 구현 상태

- `News`, `CollectionRun`, `ProviderCollectionResult` 도메인 모델
- 뉴스 URL hash 기반 중복 방어
- 제목 fingerprint 생성
- 수집 키워드 기준 provider 호출
- 같은 수집 batch 안의 동일 URL 병합
- 저장 전 기존 URL hash 조회
- 저장 중 unique 충돌을 중복으로 집계
- provider별 수집 결과를 `CollectionRun`에 기록
- Google News RSS provider
- `user-service` 내부 API 기반 활성 키워드 조회
- scheduler 기반 자동 수집 진입점
- internal API 기반 수동 수집 진입점
- 단일 인스턴스 기준 중복 실행 방지
- MongoDB reactive persistence adapter

## 수집 흐름

```text
Scheduler 또는 Internal API
  -> NewsCollectionExecutor
      -> CollectNewsUseCase
          -> KeywordReaderPort
              -> user-service internal active keywords API
          -> NewsProviderPort
              -> Google News RSS
          -> NewsPersistencePort
              -> MongoDB news
          -> CollectionRunPersistencePort
              -> MongoDB collection_runs
```

`CollectNewsCommand.keywords`가 비어 있으면 `user-service`에서 활성 키워드를 조회한다.
수동 실행 요청에서 키워드를 직접 넘기면 해당 키워드만 수집한다.

## API

뉴스 provider 연결 확인:

```http
GET /api/v1/internal/providers/news/{source}/health?keyword=NVIDIA
```

현재 지원 source:

- `GOOGLE`

이 API는 외부 provider 호출과 응답 파싱까지만 확인한다.
뉴스 저장과 `CollectionRun` 기록은 하지 않는다.

응답 예시:

```json
{
  "success": true,
  "data": {
    "source": "GOOGLE",
    "keyword": "NVIDIA",
    "fetchedCount": 10,
    "samples": [
      {
        "title": "NVIDIA ...",
        "url": "https://news.google.com/...",
        "publishedAt": "2026-05-30T00:00:00Z"
      }
    ]
  },
  "error": null
}
```

수동 뉴스 수집:

```http
POST /api/v1/internal/collections/news
```

요청 body는 생략할 수 있다. 생략하면 활성 키워드를 자동 조회한다.
현재 외부 provider 연결 확인 단계에서는 user-service 연동 없이 키워드를 직접 넘겨 collector 단독으로 smoke test를 진행한다.

```json
{
  "keywords": ["NVIDIA", "AI 반도체"],
  "sources": ["GOOGLE"]
}
```

정상 응답:

```json
{
  "success": true,
  "data": {
    "id": "018f...",
    "targetType": "NEWS",
    "status": "SUCCEEDED",
    "startedAt": "2026-05-30T00:00:00Z",
    "finishedAt": "2026-05-30T00:00:03Z",
    "requestedKeywords": 2,
    "collectedCount": 10,
    "duplicateCount": 3,
    "failureCount": 0,
    "failureReason": null
  },
  "error": null
}
```

이미 수집이 실행 중이면 `409 CONFLICT`를 반환한다.

## Scheduler

뉴스 수집 scheduler 설정:

```yaml
kachi:
  collector:
    scheduler:
      news:
        enabled: true
        fixed-delay: 10m
        initial-delay: 30s
```

기본 설정은 `application.yaml`에 두고, local/test 프로필에서는 scheduler를 기본 비활성화한다.
로컬에서 자동 수집까지 확인하려면 `KACHI_COLLECTOR_NEWS_SCHEDULER_ENABLED=true`를 지정한다.

## 단일 인스턴스 실행 가정

현재 중복 실행 방지는 JVM 내부 `AtomicBoolean` lock으로 처리한다.

이 방식은 단일 collector 인스턴스 안에서는 scheduler와 수동 API의 동시 실행을 막을 수 있다.
하지만 여러 collector 인스턴스를 동시에 띄우는 분산 환경에서는 인스턴스별로 lock이 따로 존재하므로 충분하지 않다.

분산 환경으로 확장할 때는 Redis, MongoDB, 또는 별도 coordination 저장소 기반 distributed lock으로 교체한다.

## 외부 연동

### user-service

활성 키워드 조회:

```text
GET {KACHI_USER_SERVICE_BASE_URL}/api/v1/internal/keywords/active
```

설정:

```yaml
kachi:
  collector:
    clients:
      user-service:
        base-url: http://localhost:8081
        active-keywords-path: /api/v1/internal/keywords/active
        timeout: 3s
        max-in-memory-size: 262144
```

### Google News RSS

초기 뉴스 provider는 Google News RSS다.
API key 없이 수집 흐름을 검증할 수 있지만, 공식 안정 JSON API가 아니므로 응답 구조나 접근 제한이 바뀔 수 있다.

상세 설정 근거는 [collector-service WebClient 설정](../docs/collector-webclient-설정.md)을 따른다.

## 저장소

`collector-service`는 MongoDB를 사용한다.

저장 대상:

- `news`: 수집된 뉴스
- `collection_runs`: 수집 실행 기록

로컬 MongoDB는 `infra/docker-compose.mongo.yml`로 실행한다.

```sh
docker compose -f infra/docker-compose.yml -f infra/docker-compose.mongo.yml up -d
```

## 실행

로컬 프로필 기본값:

```text
server.port=8082
MONGO_HOST=localhost
MONGO_PORT=27017
MONGO_DATABASE=kachi_collector
KACHI_USER_SERVICE_BASE_URL=http://localhost:8081
KACHI_COLLECTOR_NEWS_SCHEDULER_ENABLED=false
```

실행:

```sh
./gradlew :collector-service:bootRun
```

Google RSS 연결 확인:

```sh
curl "http://localhost:8082/api/v1/internal/providers/news/GOOGLE/health?keyword=NVIDIA"
```

collector 단독 뉴스 수집 smoke:

```sh
curl -X POST "http://localhost:8082/api/v1/internal/collections/news" \
  -H "Content-Type: application/json" \
  -d '{"keywords":["NVIDIA"],"sources":["GOOGLE"]}'
```

테스트:

```sh
./gradlew :collector-service:test
```

## 다음 작업

- `CollectionRun` 조회 API 추가
- provider 실패 사유 세분화
- Google RSS 원문 URL 정규화
- Google RSS 제목/언론사명 정규화
- Google RSS 동일 기사 URL 변형 보정
- Naver/Finnhub provider 추가
- 분산 실행이 필요해지는 시점에 distributed lock 도입
