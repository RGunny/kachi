# Kachi

> 관심 키워드 기반 뉴스 수집, AI 요약, 알림 발송 서비스

---

## 1. 프로젝트 개요

Kachi는 사용자가 등록한 관심 키워드를 기준으로 뉴스와 시장 데이터를 수집하고, AI로 키워드를 확장하거나 뉴스를 요약한 뒤, Slack/Discord/Telegram 같은 외부 채널로 알림을 발송하는 서비스다.

### 구현 체크리스트

- [x] 멀티모듈 프로젝트
- [x] 도메인 문서 초안
- [x] 헥사고날 아키텍처 패키지 규칙 정리
- [x] `user-service` User 도메인 모델
- [x] `user-service` Keyword 도메인 모델
- [x] 도메인 값 객체 (`UserId`, `KeywordId`, `Email`, `Nickname`, `KeywordName`)
- [x] User / Keyword 도메인 테스트
- [ ] `user-service` application port / service
- [ ] `user-service` persistence adapter
- [ ] `user-service` web adapter
- [ ] OAuth2 / JWT / Refresh Token
- [ ] `collector-service`
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

현재는 `user-service`의 User / Keyword 도메인 모델과 테스트를 구현 중이다.

도메인 세부 규칙은 [도메인 모델](./docs/도메인모델.md)을 기준으로 관리한다.  
설계 결정의 배경과 trade-off는 [decisions](./docs/decisions)에 기록한다.

---

## 6. 실행 / 검증

현재 도메인 테스트:

```sh
./gradlew :user-service:test
```

---

## 7. 문서

| 문서 | 내용 |
| --- | --- |
| [용어사전](./docs/용어사전.md) | Kachi 도메인 용어 정의 |
| [도메인 모델](./docs/도메인모델.md) | bounded context, aggregate, value object, 도메인 규칙 |
| [아키텍처](./docs/아키텍처.md) | 헥사고날 패키지 구조, 의존 규칙, ArchUnit 검증 방침 |
| [001. user-service에 Keyword 포함](./docs/decisions/001-user-service에-keyword-포함.md) | Keyword 경계 결정 |
| [002. ArchUnit으로 아키텍처 검증](./docs/decisions/002-archunit으로-아키텍처-검증.md) | 아키텍처 규칙 자동 검증 결정 |
| [003. UUID v7과 ID Value Object 사용](./docs/decisions/003-uuid-v7과-id-value-object-사용.md) | 식별자 생성 전략과 타입 분리 결정 |
