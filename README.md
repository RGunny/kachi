# Kachi

> 관심 키워드 기반 뉴스 수집, AI 요약, 알림 발송 서비스

---

## 1. 프로젝트 개요

Kachi는 사용자가 등록한 관심 키워드를 기준으로 뉴스와 시장 데이터를 수집하고, AI로 키워드를 확장하거나 뉴스를 요약한 뒤, Slack/Discord/Telegram 같은 외부 채널로 알림을 발송하는 서비스다.

### 구현 체크리스트

- [x] 멀티모듈 프로젝트
- [x] 도메인 문서 초안
- [x] 헥사고날 아키텍처 패키지 규칙 정리
- [x] `user-service` 기본 기능
- [x] `collector-service` 뉴스 수집 기본 기능
- [ ] `ai-service`
- [ ] `notification-service`
- [ ] `history-service`

---

## 2. 기술 스택

| 영역 | 기술 | 버전 |
| --- | --- | --- |
| Language | Kotlin | 2.2.21 |
| Language | Java | 21 |
| Framework | Spring Boot | 4.0.6 |
| Build | Gradle Kotlin DSL | - |
| Database | MySQL, MongoDB | - |
| Cache / Messaging | Redis, Kafka | - |
| Architecture | Hexagonal Architecture, Domain Model Pattern | - |

세부 기술 선택과 트레이드오프는 [문서](./docs)를 기준으로 관리한다.

---

## 3. 서비스 구조

| 서비스 | 언어 | 프레임워크 | DB | 역할 |
| --- | --- | --- | --- | --- |
| `user-service` | Kotlin | Spring MVC | MySQL | 사용자 인증, 사용자 상태, 관심 키워드 관리 |
| `collector-service` | Kotlin | WebFlux | MongoDB | 뉴스/시장 데이터 수집 |
| `ai-service` | Kotlin | WebFlux | MongoDB | 키워드 확장, 뉴스 요약 |
| `notification-service` | Kotlin | WebFlux | MySQL | Slack/Discord/Telegram 알림 발송 |
| `history-service` | Java | Spring Batch | MySQL | 사용자 활동/알림/요약 이력 적재 및 통계 집계 |

---

## 4. 이벤트 흐름

```text
user-service
  └─ 관심 키워드 등록
      -> ai-service
          └─ 키워드 확장
              -> collector-service
                  └─ 뉴스/시장 데이터 수집
                      -> ai-service
                          └─ 뉴스 요약
                              -> notification-service
                                  └─ 알림 발송
                                      -> history-service
                                          └─ 이력 적재 / 통계 집계
```

---

## 5. 현재 진행 상태

현재는 `user-service`와 `collector-service`의 기본 기능을 구현 중이다.

도메인 세부 규칙은 [도메인 모델](./docs/도메인모델.md)을 기준으로 관리한다.  
설계 결정의 배경과 trade-off는 [decisions](./docs/decisions)에 기록한다.

| 서비스 | 진행 상태 | 상세 문서 |
| --- | --- | --- |
| `user-service` | 사용자, 키워드, OAuth2/JWT, refresh token, MySQL/Redis 저장소 기본 흐름 구현 | 작성 예정 |
| `collector-service` | 뉴스 도메인, Google RSS provider, user-service 키워드 조회, MongoDB 저장, scheduler/internal API 실행 진입점 구현 | [collector-service README](./collector-service/README.md) |
| `ai-service` | 미구현 | - |
| `notification-service` | 미구현 | - |
| `history-service` | 미구현 | - |

---

## 6. 실행 / 검증

로컬 인프라 실행:

```sh
docker compose -f infra/docker-compose.yml -f infra/docker-compose.mysql.yml -f infra/docker-compose.redis.yml -f infra/docker-compose.mongo.yml up -d
```

테스트:

```sh
./gradlew :user-service:test
./gradlew :collector-service:test
```

테스트 분류와 인프라 연동 테스트 기준은 [테스트 전략](./docs/테스트전략.md)을 따른다.

---

## 7. 문서

| 문서 | 내용 |
| --- | --- |
| [용어사전](./docs/용어사전.md) | Kachi 도메인 용어 정의 |
| [도메인 모델](./docs/도메인모델.md) | bounded context, aggregate, value object, 도메인 규칙 |
| [아키텍처](./docs/아키텍처.md) | 헥사고날 패키지 구조, 의존 규칙, API 버전 정책, ArchUnit 검증 방침 |
| [테스트 전략](./docs/테스트전략.md) | unit, slice, integration, e2e 테스트 분류와 인프라 테스트 기준 |
| [collector-service WebClient 설정](./docs/collector-webclient-설정.md) | 외부 뉴스 provider WebClient 설정값과 근거 |
| [001. user-service에 Keyword 포함](./docs/decisions/001-user-service에-keyword-포함.md) | Keyword 경계 결정 |
| [002. ArchUnit으로 아키텍처 검증](./docs/decisions/002-archunit으로-아키텍처-검증.md) | 아키텍처 규칙 자동 검증 결정 |
| [003. UUID v7과 ID Value Object 사용](./docs/decisions/003-uuid-v7과-id-value-object-사용.md) | 식별자 생성 전략과 타입 분리 결정 |
| [004. JWT Access/Refresh Token 정책](./docs/decisions/004-jwt-access-refresh-token-정책.md) | 토큰 분리, TTL, secret 관리, claim 범위 결정 |
| [005. 로컬 인프라 Docker Compose 구성](./docs/decisions/005-로컬-인프라-docker-compose-구성.md) | 로컬 MySQL/Redis 실행 구성과 Docker Compose 분리 기준 |
| [006. OAuth2 로그인 흐름](./docs/decisions/006-oauth2-로그인-흐름.md) | OAuth2 provider 응답 정규화, 사용자 식별, token 발급 흐름 |
| [007. user-service 운영성 기본 설정](./docs/decisions/007-user-service-운영성-기본설정.md) | health endpoint, graceful shutdown 기본 설정 |
| [008. collector-service 뉴스 수집 실행 모델](./docs/decisions/008-collector-service-뉴스-수집-실행-모델.md) | scheduler/internal API 진입점과 단일 인스턴스 lock 결정 |
