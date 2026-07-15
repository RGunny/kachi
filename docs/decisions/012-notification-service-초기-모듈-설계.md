# notification-service 초기 모듈 설계

## 배경

- 알림 도메인은 대량 요청, 외부 발송 I/O, 일시 실패 재시도, 중복 발송 방지가 핵심이다.
- Spring WebFlux와 Kotlin Coroutine을 사용해 외부 발송 I/O를 논블로킹으로 처리하고, 비동기 처리 흐름을 구조적으로 설계한다.
- `kachi` 전체는 MSA로 구성하지만, `notification`은 그 안의 하나의 bounded context다.
- `notification` 내부에서는 API, worker, admin의 실행 특성이 다르므로 런타임은 분리하되, 알림 상태 전이와 멱등 규칙은 하나의 도메인 규칙으로 유지한다.

## 결정

초기 notification 모듈은 다음 네 개로 시작한다.

```text
include("notification-contract")
include("notification-core")
include("notification-service")
include("notification-worker")
```

`notification-admin`은 초기부터 별도 런타임으로 분리하지 않는다. 운영용 조회/재처리 API는 우선 `notification-service` 안에 두고, 보안/배포/스케일링 요구가 커질 때 별도 모듈로 분리한다.

## 전체 구조

```text
                       notification-contract
                                ▲
                                |
                       notification-core
                    domain + application layer
                         ▲              ▲
                         |              |
          notification-service     notification-worker
          Web/Admin/Outbox         Kafka/Vendor sender
          Boot runtime             Boot runtime
```

의존 방향은 다음과 같다.

```text
user-service / collector-service / ai-service -> notification-contract

notification-service -> notification-core -> notification-contract
notification-worker  -> notification-core -> notification-contract
```

`notification-service`와 `notification-worker`는 각각 독립 기동되는 Spring Boot 애플리케이션이지만, 별도 도메인 모델을 갖는 서비스가 아니다. 둘은 같은 notification bounded context를 실행하는 서로 다른 adapter/runtime이다.

## 모듈 책임

### notification-contract

서비스 간 Kafka 메시지 계약을 담는 공유 모듈이다. Spring Boot 애플리케이션이 아니며, 가능하면 `kotlin("jvm")`만 적용한다.

포함 대상:

- `NotificationRequestedEvent`
- `NotificationDispatchEvent`
- `NotificationChannel`
- `NotificationPriority`
- `NotificationType`
- template variables DTO
- request id / idempotency key value object
- topic name, header key, schema version 같은 메시지 계약 상수

포함하지 않을 것:

- DB entity
- Redis/Kafka/WebFlux 구현체
- 알림 발송 도메인 로직
- Spring Boot application 설정
- 특정 vendor 연동 코드

`notification-contract`는 여러 서비스가 함께 참조하는 계약 모듈이므로 변경 비용이 크다. 내부 구현 편의를 위한 클래스는 넣지 않고, 외부 서비스가 Kafka 메시지를 만들거나 읽는 데 필요한 타입만 둔다.

`NotificationDispatchEvent`는 같은 notification bounded context 안의 `notification-service`와 `notification-worker` 사이에서 사용하는 Kafka payload 계약이다.
두 런타임은 별도 애플리케이션으로 배포되고 Kafka topic을 통해 연결되므로, service/worker adapter에 같은 DTO를 중복 정의하지 않고 contract 모듈에서 공유한다.

나중에 Kafka Avro와 Schema Registry를 도입하면 이 모듈은 Kotlin DTO 공유 모듈에서 Avro schema 중심의 계약 모듈로 변경한다.

### notification-core

헥사고날 아키텍처의 안쪽 도메인+애플리케이션 레이어다. 알림 도메인 규칙과 유스케이스를 담는다.

```text
notification-core
  domain
    Notification
    NotificationStatus
    NotificationHistory
    NotificationOutbox
    NotificationOutboxStatus
    RetryFailure
    RetryPolicy
    RetryDecision

  application
    port
      in
        RequestNotificationUseCase
        PublishNotificationDispatchUseCase
        DispatchNotificationUseCase
      out
        NotificationPersistencePort
        NotificationOutboxPersistencePort
        NotificationDispatchPublisher
        NotificationEventSerializer
        NotificationSender
        NotificationDeduplicationPort
        NotificationIdempotencyKeyPort
    service
      RequestNotificationService
      PublishNotificationDispatchService
      DispatchNotificationService
      NotificationSenderRouter
```

`notification-core`는 다음 규칙을 지킨다.

- domain은 Spring, Kafka, Redis, WebClient, persistence entity에 의존하지 않는다.
- application service도 Spring stereotype annotation에 의존하지 않는다.
- application은 port를 통해 외부 시스템을 추상화한다.
- adapter 구현체는 `notification-service` 또는 `notification-worker`에 둔다.
- contract event와 domain/application command는 분리한다.
- `notification-core`는 `kotlin("jvm")` 기반 POJO 모듈로 유지하고, Spring Boot plugin이나 Spring component scan 대상이 되지 않는다.

`core`에 domain만 두지 않고 application까지 두는 이유는 알림 도메인의 핵심이 단순 데이터 모델이 아니라 처리 규칙이기 때문이다. 
멱등, 상태 전이, retry, DLT, vendor idempotency key, claim, 재처리 규칙이 api/worker/admin에 흩어지면 같은 알림에 대해 서로 다른 상태 전이가 생길 수 있다.

`NotificationHistory`는 별도 도메인 개념이며 저장 구조상 `notification_histories` 별도 collection에 둔다.
대량 운영에서 Notification document가 history array 때문에 커지는 문제를 피하고, 운영 audit 조회와 paging을 독립적으로 설계하기 위해서다.
상태 변경과 상태 전이 이력 insert는 같은 MongoDB transaction에서 확정되어야 하므로, 이 이력을 별도 Kafka consumer나 별도 모듈에서 후처리하지 않는다.

`history-service`가 나중에 담당할 이력은 사용자 장기 조회와 통계 적재용 이력이다.
notification 내부의 `NotificationHistory`는 알림 한 건의 상태 전이 감사 로그이며, `history-service`의 장기 이력과 책임이 다르다.

`Notification.DEAD` 수동 복구는 기존 notification 현재 상태 document를 조건부 update하고 새 dispatch outbox를 insert한다.
상태는 `DEAD -> REQUESTED`로 되돌리고, 다음 outbox publisher tick이 `notification.dispatch`를 다시 발행한다.
이때 Notification 상태 변경, `NotificationHistory` insert, 새 outbox insert는 같은 MongoDB transaction에서 확정한다.

운영 API는 `notification-service` 안에서 제공한다.
DEAD notification 목록은 `notifications` 현재 상태 document를 조회하고, 상태 전이 근거는 `notification_histories`를 notification 단위로 조회한다.

### notification-service

HTTP API, 운영 API, outbox 발행/복구를 담당하는 런타임이다.

```text
notification-service
  adapter
    in
      web
      web.admin
      scheduler
    out
      persistence
      messaging
      redis
      serialization
  config
  NotificationServiceApplication
```

초기 책임:

- 내부/운영 HTTP API 제공
- HTTP request 또는 Kafka `notification.requested` event 수신
- 알림 요청을 받는 경우 Redis/DB 멱등 처리
- Notification 저장
- Outbox 저장
- Kafka publish
- Outbox recovery scheduler
- DEAD notification/outbox 조회와 재처리 API

`kachi`의 주 인입은 Kafka 이벤트지만, HTTP 인입과 outbox 흐름도 남겨둔다. 이유는 다음과 같다.

- 다른 서비스나 내부 도구가 HTTP로 알림을 요청할 수 있다.
- 초기 구현 단계 최초 인입을 HTTP request 로 편하게 처리한다.
- HTTP request -> DB insert -> outbox insert -> Kafka publish 구간의 유실 방지 설계를 명확히 보여줄 수 있다.

단, HTTP 접수는 핵심 경로가 아니라 보조 경로다. `kachi` 내부 서비스 간 기본 연동은 Kafka 이벤트를 우선한다.

### notification-worker

Kafka consume과 실제 외부 채널 발송을 담당하는 런타임이다.

```text
notification-worker
  adapter
    in
      kafka
    out
      persistence
      redis
      sender
  config
  NotificationWorkerApplication
```

초기 책임:

- `notification.dispatch` consume
- Redis `SETNX` 기반 중복 방지
- DB unique key / 상태 전이 가드 기반 멱등 보장
- `PUBLISHED` 또는 `RETRY_WAIT` 알림 claim
- Slack, Telegram, Discord 실제 발송
- SMS/EMAIL 등은 실제 발송에서는 제외하고, 별도 dummy server 구현하여 발송하고, 채널에 대한 추상화로 확장성있는 설계를 보여준다.
- retryable / non-retryable 실패 분류
- retry topic 또는 DLT 처리
- vendor 호출 시 idempotency key 재사용

worker는 API와 부하 특성이 다르다. 
외부 vendor 지연, rate limit, 장애 전파, Kafka rebalance, retry storm 같은 이슈가 API 런타임과 다르게 발생한다. 따라서 초기부터 별도 런타임으로 분리한다.

## 헥사고날 유지 방식

헥사고날 아키텍처를 kachi 프로젝트와 동일하게 설계하려면, notification-service, notification-worker 모듈 모두 domain/application/adapter 를 포함해야하나 고민했다.
하지만 이전 알림시스템 구현 경험에서 위 방식을 채택했을 때, 도메인 중복/비즈니스 중복이 빈번하게 발생하고 동일 내용의 분리된 소스를 관리하기 어려움이 있었다.
**따라서 핵심 도메인 규칙과 애플리케이션 비즈니스는 notification-core에 모아 중복 구현을 방지하고, 외부 연동 adapter는 별도 런타임 모듈로 분리해 장애 격리, 독립 확장, 운영 안정성을 확보한다.**

`notification-core`는 Spring-free POJO 스타일로 유지한다.
따라서 core의 application service에는 `@Service`, `@Component`, `@Transactional` 같은 Spring annotation을 붙이지 않는다.
core service는 생성자 주입을 받는 일반 Kotlin class이며, Spring bean 등록은 각 runtime의 `config` 패키지에서 composition root 역할로 수행한다.

```text
notification-core
  RequestNotificationService      (Spring annotation 없음)
  PublishNotificationDispatchService
  DispatchNotificationService

notification-service
  config
    -> RequestNotificationService를 RequestNotificationUseCase bean으로 조립
    -> PublishNotificationDispatchService를 PublishNotificationDispatchUseCase bean으로 조립

notification-worker
  config
    -> DispatchNotificationService를 DispatchNotificationUseCase bean으로 조립
```

runtime adapter 구현체는 Spring stereotype을 사용할 수 있다.
예를 들어 web controller, Kafka listener, Mongo persistence adapter, Redis adapter, Kafka publisher는 `@RestController`, `@Component`, `@Repository` 같은 Spring annotation으로 등록한다.
반면 core service는 runtime framework를 모르는 순수 객체로 유지한다.

이 방식은 과거 XML 빈 설정 방식처럼 레거시 스타일로 회귀하는 것이 아니라, 헥사고날 아키텍처의 composition root를 명시하기 위한 선택이다.
Spring Boot 자동 component scan은 runtime adapter를 발견하는 데 사용하고, core use case 조립은 runtime별 configuration에서 명시한다.
service와 worker가 같은 core service를 서로 다른 adapter/policy 조합으로 사용할 수 있기 때문이다.

```text
External Actor
  -> runtime adapter in
  -> core application port.in
  -> core application service
  -> core domain
  -> core application port.out
  -> runtime adapter out
  -> External System
```

예시:

```text
HTTP request
  -> notification-service Controller
  -> core RequestNotificationUseCase
  -> core NotificationOutboxPersistencePort
  -> service persistence adapter
  -> core NotificationDispatchPublisher
  -> service Kafka adapter
```

```text
Kafka message
  -> notification-worker Listener
  -> core DispatchNotificationUseCase
  -> core DeduplicationMarker port
  -> worker Redis adapter
  -> core NotificationSender port
  -> worker Slack/Telegram/Discord/SMS adapter
```

`notification-service`와 `notification-worker` 안에 각각 `domain`을 만들지 않는다. 이 둘은 서로 다른 bounded context가 아니라 같은 notification 도메인의 실행 형태이기 때문이다. 도메인을 런타임별로 복제하면 `NotificationStatus`, 상태 전이 규칙, 멱등 판단이 서로 다르게 진화할 위험이 있다.

## 핵심 설계 원칙

알림 시스템은 외부 채널 발송을 포함하므로 단순 요청/응답 API보다 장애 지점이 많다. 따라서 초기 설계의 우선순위는 빠른 발송보다 중복 방지, 유실 방지, 재처리 가능성, 장애 격리다.

### 멱등성

알림은 같은 요청이 여러 번 도착할 수 있다는 전제로 처리한다.

- producer 재시도
- Kafka at-least-once consume
- consumer rebalance
- retry topic 재진입
- DLT 수동 재처리
- HTTP client 재시도

중복 발송을 막기 위해 멱등성은 여러 계층에서 방어한다.

```text
1차: Redis SETNX
2차: DB unique key
3차: Notification 상태 전이 가드
4차: vendor idempotency key
```

Redis는 빠른 중복 차단을 담당한다. DB unique key는 Redis 장애, TTL 만료, race condition을 보완한다. 도메인 상태 전이 가드는 이미 `SENT`, `DEAD` 같은 종착 상태에 도달한 알림이 다시 발송 흐름으로 들어가는 것을 막는다. 외부 vendor가 idempotency key를 지원하는 경우 같은 알림에는 같은 key를 재사용한다.

### 상태 전이

알림의 생명주기는 도메인 상태로 명시한다.

```text
REQUESTED
  -> PUBLISHED
  -> PROCESSING
  -> SENT

PROCESSING
  -> FAILED
  -> RETRY_WAIT
  -> PROCESSING

FAILED / RETRY_WAIT
  -> DEAD
```

상태 전이는 `notification-core`의 domain/application 규칙으로 관리한다. adapter는 상태를 직접 조작하지 않고 usecase를 통해서만 변경한다.

### 순서 보장

전역 순서는 보장하지 않는다. 전역 순서를 보장하려면 단일 partition 또는 강한 직렬화가 필요해 전체 처리량이 크게 제한된다.

초기에는 수신자 단위 순서를 보장한다.

```text
Kafka partition key = recipient 또는 userId
```

같은 수신자의 알림은 같은 partition으로 보내 순서를 유지하고, 전체 처리량은 partition 수와 worker consumer 수로 확장한다.

### 실패 분류

외부 발송 실패는 retryable과 non-retryable로 구분한다.

```text
retryable
  - vendor 5xx
  - timeout
  - rate limit
  - 일시적인 network 오류

non-retryable
  - 잘못된 recipient
  - 인증/권한 오류
  - 지원하지 않는 channel
  - payload validation 실패
```

retryable 실패는 제한된 횟수만 재시도한다. 재시도 한도를 초과하면 `DEAD` 상태로 전이하고 운영자가 조회/재처리할 수 있게 남긴다. non-retryable 실패는 즉시 `DEAD`로 전이한다.

### 채널 확장

발송 채널은 `NotificationSender` 인터페이스와 `SenderRouter`로 확장한다.

```text
NotificationSender
  - SlackNotificationSender
  - TelegramNotificationSender
  - DiscordNotificationSender
  - SmsDummyNotificationSender
```

신규 채널은 sender 구현체와 설정을 추가하는 방식으로 확장한다. application service는 특정 vendor API나 WebClient 세부 구현에 의존하지 않는다.

### 재처리 가능성

자동 재시도만으로 복구할 수 없는 실패는 운영자가 확인하고 재처리할 수 있어야 한다.

- `DEAD` 알림 조회
- DLT 메시지 조회
- 재처리
- 폐기
- 실패 사유와 마지막 예외 기록

worker는 `notification.dispatch.dlt` 메시지를 `notification_dlt_messages` collection에 먼저 영속화한다.
DLT 저장은 원본 Kafka record 위치 기준 upsert로 처리해 DLT consumer 재처리 중복을 막는다.
`notification-service` admin API는 저장된 DLT 메시지를 상태별로 조회한다.

초기에는 이 기능을 `notification-service`의 `/admin` API로 제공한다. 별도 admin 런타임은 운영 기능의 배포, 인증, 스케일링 요구가 커질 때 분리한다.

## 처리 흐름

### Kafka 요청 인입

`kachi` 내부 서비스의 기본 알림 요청 흐름이다.

```text
user-service / collector-service / ai-service
  -> Kafka topic: notification.requested
  -> notification-service consume
  -> Redis request dedupe
  -> Notification REQUESTED 저장
  -> NotificationOutbox PENDING 저장
  -> notification.dispatch publish
  -> Notification PUBLISHED
  -> notification-worker consume
  -> Redis dispatch dedupe
  -> PUBLISHED 또는 RETRY_WAIT claim
  -> SenderRouter
  -> Slack/Telegram/Discord/SMS sender
  -> SENT / RETRY_WAIT / DEAD
  -> retry topic 또는 DLT
```

초기 topic 예시:

```text
notification.requested
notification.dispatch
notification.dispatch.retry
notification.dispatch.dlt
```

partition key:

```text
recipient 또는 userId
```

같은 수신자 단위 순서를 보장하고, 전체 처리량은 partition과 consumer 수로 확장한다.

요청 접수 topic과 발송 실행 topic은 분리한다.
`notification.requested`는 notification-service가 접수하고, worker는 service가 outbox를 통해 발행한 `notification.dispatch`만 처리한다.

### HTTP 보조 인입

내부 도구, 운영자 요청, 향후 외부 API를 위한 보조 흐름이다.

```text
HTTP request
  -> notification-service
  -> Redis SETNX
  -> Notification 저장
  -> Outbox 저장
  -> notification.dispatch publish
  -> notification-worker consume
```

outbox는 요청을 받은 뒤 worker용 dispatch topic에 발행하는 구간의 유실을 막기 위한 장치다.
따라서 HTTP 인입뿐 아니라 Kafka `notification.requested` 인입도 notification-service가 접수하면 같은 outbox 흐름을 탄다.
worker 경로의 핵심은 dispatch message에 대한 at-least-once 처리와 멱등성이다.

### 발송 상태 흐름

```text
notification.dispatch
  -> Redis dispatch dedupe
  -> PUBLISHED 또는 RETRY_WAIT에서 PROCESSING claim
  -> channel sender 호출
     -> 성공: SENT
     -> retryable 실패: RETRY_WAIT
     -> 재시도 한도 초과: DEAD
     -> non-retryable 실패: DEAD
```

Kafka offset은 처리 완료 이후 commit한다. 이 방식은 장애 시 중복 처리가 발생할 수 있지만 메시지 유실 가능성을 줄인다. 중복 처리는 멱등성 설계로 흡수한다.

### Outbox 흐름

HTTP 인입, Kafka `notification.requested` 인입, 운영자 재처리처럼 DB 상태 변경과 Kafka publish가 함께 필요한 경우 outbox를 사용한다.

```text
HTTP/Kafka/Admin request
  -> Notification 저장
  -> Outbox PENDING 저장
  -> transaction commit
  -> notification.dispatch publish
  -> 성공: Outbox PUBLISHED
  -> 실패: Outbox PENDING 유지, nextRetryAt 갱신
  -> scheduler가 재발행
  -> 한도 초과: Outbox DEAD
```

Outbox는 worker 발송 재시도와 목적이 다르다. Outbox는 DB commit 이후 Kafka publish 유실을 막기 위한 발행 보장 장치이고, worker retry는 Kafka 메시지를 받은 뒤 외부 vendor 발송 실패를 복구하기 위한 장치다.
초기 outbox publish retry는 실패 3회 누적 시 `DEAD`로 전이한다.

요청 접수와 dispatch 발행 흐름의 세부 결정은 [013. notification 요청 접수와 dispatch 발행 흐름](./013-notification-request-service-outbox-dispatch-flow.md)을 따른다.
dispatch 실패 분류와 Kafka retry 연결은 [014. notification dispatch 실패 분류와 Kafka retry 연결](./014-notification-dispatch-retry-classification.md)을 따른다.
outbox claim, stale recovery, retry 정책은 [015. notification outbox 발행 보장과 recovery 정책](./015-notification-outbox-publish-runtime.md)을 따른다.
worker의 Slack/Discord/Telegram sender 구조와 설정 구성은 [016. notification-worker vendor sender 구조와 설정 구성](./016-notification-worker-vendor-sender-구조.md)을 따른다.

## WebFlux/Kotlin 구현 기준

권장:

- 외부 vendor 호출은 `WebClient` 사용
- MongoDB를 쓴다면 reactive MongoDB repository 사용
- Redis도 가능하면 reactive Redis template 사용
- application service는 Kotlin Coroutine 기반 `suspend` 함수로 구성
- 여러 외부 호출 조합은 structured concurrency로 범위를 명확히 관리
- blocking JPA/JDBC를 reactive/coroutine 흐름 안에서 직접 호출하지 않기

주의:

- Kafka consumer 자체는 Spring Kafka listener 모델을 사용할 수 있다.
- listener 내부에서 suspend 함수를 호출할 경우 ack 시점과 coroutine 실행 완료 시점을 명확히 맞춘다.
- 처리 완료 전에 offset을 commit하면 장애 시 유실 가능성이 생긴다.
- 처리 완료 후 commit하면 at-least-once가 되므로 멱등 설계가 필수다.

## 트레이드오프

### 장점

- API와 worker를 별도 런타임으로 분리해 부하 특성, 장애 격리, 스케일링 단위를 다르게 가져갈 수 있다.
- 알림 도메인 규칙은 `notification-core`에 모아 상태 전이와 멱등 규칙의 중복 구현을 줄인다.
- `notification-contract`를 분리해 다른 서비스가 알림 이벤트를 명시적인 계약으로 발행할 수 있다.
- HTTP/outbox와 Kafka 요청 인입을 모두 지원해 운영 도구와 EDA 흐름을 함께 가져갈 수 있다.
- Avro 도입 시 contract 모듈을 schema-first 계약 모듈로 자연스럽게 전환할 수 있다.

### 단점

- 초기 모듈 수가 단일 `notification-service`보다 많다.
- runtime adapter가 `notification-core`의 모든 usecase/port를 의존할 수 있어, core가 커지면 필요 이상의 컴파일 의존이 생길 수 있다.
- persistence adapter가 service와 worker 양쪽에 필요해 mapping 코드가 중복될 수 있다.
- 트랜잭션 경계를 core에 둘지 runtime adapter에 둘지 명확한 기준이 필요하다.
- contract 변경이 여러 producer/consumer 배포와 연결되므로 schema evolution 관리가 필요하다.
