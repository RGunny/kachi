# 009. 외부 뉴스 provider 연동 기준

## 배경

`collector-service`는 사용자 관심 키워드를 기준으로 외부 뉴스 provider를 호출한다.

초기 구현에는 다음 provider가 있다.

- Google News RSS
- Naver Search API 뉴스 검색
- Finnhub Company News

각 provider는 인증 방식, 검색 파라미터, 응답 형식, rate limit, 데이터 성격이 다르다.
이 차이를 application/domain 계층에 노출하면 수집 유스케이스가 provider별 구현 세부사항에 오염된다.

## 결정

외부 뉴스 provider는 모두 `NewsProviderPort` 구현체로 연결한다.

```text
CollectNewsService
  -> NewsProviderPort
      -> GoogleNewsRssProvider
      -> NaverNewsSearchProvider
      -> FinnhubNewsProvider
```

Provider별 HTTP client는 전용 `WebClient` bean으로 분리한다.
각 provider는 별도 properties를 가진다.

```text
kachi.collector.providers.google
kachi.collector.providers.naver
kachi.collector.providers.finnhub
```

Google News RSS는 API key가 없으므로 기본 활성화한다.
Naver와 Finnhub는 credential이 필요하므로 기본 비활성화한다.

| Provider | 기본 enabled | 인증 | 비고 |
| --- | --- | --- | --- |
| Google News RSS | `true` | 없음 | 초기 수집 흐름 검증용으로 사용 |
| Naver Search API | `false` | `X-Naver-Client-Id`, `X-Naver-Client-Secret` | credential 없으면 bean을 만들지 않음 |
| Finnhub Company News | `false` | `X-Finnhub-Token` | keyword를 company symbol로 해석 |

Credential이 필요한 provider는 enabled 상태에서 credential이 비어 있으면 애플리케이션 기동 시점에 실패시킨다.
반대로 기본값은 `enabled=false`로 두어 credential 없는 로컬/CI 실행이 깨지지 않게 한다.

Provider 응답은 adapter 안에서 `CollectedArticle`로 변환한다.

```text
provider response DTO
  -> CollectedArticle(source, title, url, publishedAt)
```

제목과 URL이 비어 있는 항목은 adapter에서 제외한다.
날짜를 파싱할 수 없거나 제공하지 않는 경우 해당 기사 `publishedAt`만 `null`로 둔다.

## 이유

### 전용 WebClient

Provider마다 base URL, endpoint path, timeout, max body size, 인증 header가 다르다.

하나의 공용 WebClient를 공유하면 설정이 섞이고, 특정 provider의 장애나 성능 특성을 분리해서 조정하기 어렵다.
따라서 provider별 WebClient bean을 두고 `@Qualifier`로 주입 대상을 명시한다.

### Credential provider 기본 비활성화

Naver와 Finnhub는 credential이 없으면 모든 호출이 실패한다.
기본 enabled를 `true`로 두면 로컬 실행, CI context load, credential 없는 배포 검증이 provider 설정 오류로 깨진다.

따라서 credential provider는 명시적으로 켠 경우에만 bean을 생성한다.

```yaml
kachi:
  collector:
    providers:
      naver:
        enabled: false
      finnhub:
        enabled: false
```

### Provider별 데이터 성격 유지

Google/Naver는 검색어 기반 뉴스 검색에 가깝다.
Finnhub Company News는 회사 symbol 기반 뉴스 조회다.

같은 `NewsProviderPort`로 연결하되, keyword 해석의 차이는 adapter 문서와 설정에 명시한다.
현재 Finnhub provider는 collector keyword를 company symbol로 보고 대문자로 정규화해 요청한다.

### 실패 분류는 다음 단계

현재 `CollectNewsService`는 provider 예외를 잡아 `ProviderFailureReason.UNKNOWN`으로 기록한다.
HTTP 401/403, 429, 5xx, timeout, invalid response를 세분화하는 작업은 별도 고도화로 남긴다.

초기 provider 추가 단계에서는 다음을 우선한다.

- provider bean wiring
- credential 기본 정책
- 요청 path/query/header 검증
- 응답 DTO -> `CollectedArticle` 변환
- 필수값 누락 항목 제외

## 결과

- application/domain 계층은 외부 provider의 HTTP 계약을 알지 않는다.
- provider 추가 시 `Properties`, `Config`, `Provider`, DTO, 단위 테스트를 같은 구조로 추가한다.
- credential이 없는 환경에서도 기본 collector-service context load가 가능하다.
- credential provider를 켰는데 설정이 비어 있으면 기동 시점에 명확히 실패한다.
- provider별 timeout과 max body size를 독립적으로 조정할 수 있다.

## 제외한 것

현재는 provider별 retry, 서킷 브레이커, rate limit backoff, 상세 실패 사유 매핑을 구현하지 않는다.
kachi 프로젝트 전체 파이프라인 구성 이후 개별 서비스별 고도화 단계에서 진행한다.
외부 provider 호출 정책이 실제 운영 데이터로 확인되면 다음 항목을 별도 결정으로 추가한다.

- provider별 retry 조건
- HTTP status별 `ProviderFailureReason` 매핑
- 429 대응 scheduler interval 또는 backoff
- provider별 서킷 브레이커
- provider 응답 품질 평가와 우선순위
