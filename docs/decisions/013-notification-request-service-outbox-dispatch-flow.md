# 013. notification 요청 접수와 dispatch 발행 흐름

## 배경

`notification`은 HTTP 요청, 내부 Kafka 이벤트, 운영자 재처리처럼 여러 인입 경로를 가질 수 있다.
인입 경로마다 저장, 멱등, Kafka 발행 로직을 따로 두면 같은 알림 요청에 대해 서로 다른 상태 전이와 중복 방지 규칙이 생긴다.

초기 설계 문서에서는 `notification.requested`를 worker가 직접 consume하는 흐름을 검토했지만, 현재 core 구현은 요청 접수와 실제 vendor 발송을 분리한다.

```text
요청 인입
  -> notification-service
  -> notification-core RequestNotificationUseCase
  -> Notification + NotificationOutbox 저장
  -> notification.dispatch 발행
  -> notification-worker
```

## 결정

모든 알림 요청 인입은 `notification-service` adapter를 통과한다.

`notification-service`는 HTTP request 또는 Kafka `notification.requested` event를 `RequestNotificationCommand`로 변환하고, `RequestNotificationUseCase`를 호출한다.
`RequestNotificationUseCase`는 `Notification`을 `REQUESTED`로 저장하고, worker에 넘길 `NotificationDispatchMessage`를 `NotificationOutbox`에 함께 저장한다.

`notification-worker`는 외부 요청 이벤트를 직접 처리하지 않는다.
worker는 `notification.dispatch` topic의 `NotificationDispatchEvent`만 consume하고 `DispatchNotificationUseCase`를 호출한다.

broker payload 계약은 `notification-contract`의 `NotificationDispatchEvent`로 둔다.
core application 내부에서는 `NotificationDispatchMessage`를 사용하고, service adapter가 이를 contract event로 직렬화한다.
worker adapter는 같은 contract event를 역직렬화해 core `DispatchNotificationCommand`로 변환한다.

## 흐름

```text
user-service / collector-service / ai-service
  -> Kafka topic: notification.requested
  -> notification-service Kafka listener
  -> RequestNotificationUseCase.request(command)
  -> Redis request dedupe
  -> Notification REQUESTED 저장
  -> NotificationOutbox PENDING 저장
  -> PublishNotificationDispatchUseCase.publishPending()
  -> Kafka topic: notification.dispatch
  -> Notification PUBLISHED
  -> notification-worker Kafka listener
  -> DispatchNotificationUseCase.dispatch(command)
  -> SenderRouter
  -> channel sender
  -> SENT / RETRY_WAIT / DEAD
```

HTTP 인입도 같은 application use case를 사용한다.

```text
HTTP request
  -> notification-service Controller
  -> RequestNotificationUseCase.request(command)
  -> Notification + NotificationOutbox 저장
  -> notification.dispatch 발행
  -> notification-worker consume
```

## Topic 역할

| Topic | Producer | Consumer | 역할 |
| --- | --- | --- | --- |
| `notification.requested` | user/collector/ai 등 외부 bounded context | notification-service | 알림 요청 접수 이벤트 |
| `notification.dispatch` | notification-service | notification-worker | 발송 실행 메시지 |
| `notification.dispatch.retry` | Kafka retry runtime | notification-worker | vendor 발송 일시 실패 재시도 |
| `notification.dispatch.dlt` | Kafka retry runtime | notification-service 또는 admin runtime | 자동 재시도 종료 메시지 보관 |

`notification.requested`와 `notification.dispatch`를 분리하는 이유는 요청 접수 보장과 vendor 발송 재시도의 실패 지점이 다르기 때문이다.
요청 접수는 `Notification + Outbox` 저장으로 보장하고, 발송 실패는 worker의 retry topic / DLT 정책으로 처리한다.

## 멱등 기준

요청 접수와 발송 실행은 서로 다른 retry 주체를 가진다.
따라서 멱등키도 분리한다.

| 단계 | 기준 키 | 목적 |
| --- | --- | --- |
| 요청 접수 | `requestId` | client 또는 upstream producer 재시도 중복 차단 |
| dispatch 실행 | `notificationId` | Kafka at-least-once, retry topic, rebalance 중복 차단 |
| vendor 호출 | `notificationId + channel` 기반 idempotency key | 외부 sender 재호출 중복 차단 |

Redis dedupe는 1차 가드이고, DB unique/CAS와 domain 상태 전이 가드가 최종 정합성을 지킨다.

## 트레이드오프

### 장점

- 인입 방식이 HTTP인지 Kafka인지와 무관하게 같은 요청 접수 규칙을 사용한다.
- 요청 저장과 dispatch 발행 대기열 저장을 같은 저장 경계에 둘 수 있다.
- worker는 vendor 발송 실행과 retry runtime 제어에 집중한다.
- 요청 접수 실패, Kafka publish 실패, vendor 발송 실패를 서로 다른 상태와 재시도 정책으로 추적할 수 있다.

### 단점

- `notification.requested`를 worker가 직접 consume하는 방식보다 한 단계가 늘어난다.
- service와 worker 사이에 `notification.dispatch` 계약을 별도로 관리해야 한다.

## 후속 구현 순서

1. `notification-service` Kafka/HTTP inbound adapter 구현
2. `notification-service` persistence/Redis/serializer adapter 구현
3. `notification-service` dispatch Kafka publisher와 outbox scheduler 구현
4. `notification-worker` dispatch Kafka listener와 retry topic 설정 구현
5. channel sender adapter 구현
