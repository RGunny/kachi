# 015. notification outbox 발행 보장과 recovery 정책

## 배경

알림 요청을 DB에 저장한 뒤 Kafka에 발행하는 구간에는 유실 윈도우가 있다.
DB commit은 성공했지만 Kafka publish 호출 전에 프로세스가 종료되거나, publish 실패 후 상태 갱신이 누락될 수 있다.

producer 설정의 `acks`나 `retries`는 publish 호출이 실제로 일어난 뒤의 broker ack 문제를 줄일 뿐, commit 이후 publish 호출 자체가 누락되는 문제를 해결하지 못한다.

## 결정

`RequestNotificationUseCase`는 `Notification`과 `NotificationOutbox`를 같은 저장 경계에서 만든다.

`PublishNotificationDispatchUseCase`는 발행 가능한 outbox를 claim한 뒤 `notification.dispatch` topic으로 발행하고, 발행 결과를 `NotificationOutbox`와 `Notification` 상태에 반영한다.

## 상태 모델

### NotificationOutbox

```text
PENDING
  -> PUBLISHING
  -> PUBLISHED

PUBLISHING
  -> PENDING  (재시도 가능)
  -> DEAD     (재시도 한도 초과)

DEAD
  -> PENDING  (운영자 수동 복구)
```

| 상태 | 의미 |
| --- | --- |
| `PENDING` | 발행 대기 또는 재시도 대기 |
| `PUBLISHING` | publisher가 CAS claim으로 처리 권한 획득 |
| `PUBLISHED` | broker 발행 성공 |
| `DEAD` | publish 자동 재시도 종료 |

허용 상태 전이는 다음과 같다.

| Current | Event | Next | 비고 |
| --- | --- | --- | --- |
| `PENDING` | claim 성공 | `PUBLISHING` | `claimedAt`, `claimedBy` 기록 |
| `PUBLISHING` | publish 성공 | `PUBLISHED` | terminal, claim 정보 제거 |
| `PUBLISHING` | retryable publish 실패, 한도 미만 | `PENDING` | `retryCount++`, `nextRetryAt` 갱신, claim 정보 제거 |
| `PUBLISHING` | retryable publish 실패, 한도 도달 | `DEAD` | 자동 재시도 종료, claim 정보 제거 |
| `PUBLISHING` | payload/serialization 계약 오류 | `DEAD` | 재시도해도 성공 가능성이 낮으므로 즉시 종료 |
| `DEAD` | admin recover | `PENDING` | retry/claim/error 필드 초기화 |

`PENDING`은 최초 발행 대기와 실패 후 재시도 대기를 모두 표현한다.
scheduler는 `PENDING` 전체가 아니라 `nextRetryAt <= now`인 row만 publishable 후보로 본다.

### Notification

outbox 발행 결과는 notification 상태에도 반영한다.

| Outbox 결과 | Notification 상태 |
| --- | --- |
| 신규 접수 | `REQUESTED` |
| dispatch publish 성공 | `PUBLISHED` |
| dispatch publish 실패 | `PUBLISH_FAILED` |
| outbox recovery 성공 | `PUBLISHED` |

`PUBLISH_FAILED`는 vendor 발송 실패가 아니라 dispatch message 발행 실패다.
vendor 발송 실패는 worker가 `FAILED`, `RETRY_WAIT`, `DEAD`로 처리한다.

`NotificationOutbox.DEAD`가 되어도 `Notification`은 `PUBLISH_FAILED`로 남는다.
outbox `DEAD`는 dispatch message 발행 자동 재시도 종료이고, notification `DEAD`는 worker가 vendor 발송 자동 재시도를 종료한 상태다.
두 상태는 실패 지점과 운영 조치가 다르므로 섞지 않는다.

## Claim 정책

여러 service 인스턴스가 같은 `PENDING` outbox를 동시에 볼 수 있다.
따라서 publish 전에 저장소 CAS로 `PENDING -> PUBLISHING` claim을 수행한다.

claim에 성공한 publisher만 Kafka publish를 호출한다.
claim에 실패한 후보는 다른 인스턴스가 처리 중인 것으로 보고 skip한다.

`PUBLISHING` 상태는 반드시 `claimedAt`, `claimedBy`를 가진다.
`PUBLISHING`에서 `PUBLISHED`, `PENDING`, `DEAD`로 빠질 때는 claim 정보를 제거한다.
claim 정보는 stale publishing 회수와 운영자 상세 조회의 기준이다.

## Stale PUBLISHING 회수

publisher가 `PUBLISHING` claim 후 publish 결과를 저장하기 전에 종료되면 outbox가 계속 `PUBLISHING`에 머무를 수 있다.

`PublishNotificationDispatchUseCase`는 `publishingVisibilityTimeout`보다 오래된 `PUBLISHING` outbox를 실패로 기록하고 재시도 대상으로 돌린다.

```text
stale PUBLISHING
  -> recordFailure("publishing-timeout")
  -> PENDING 또는 DEAD
  -> Notification PUBLISH_FAILED
```

이 회수는 중복 publish 가능성을 완전히 없애지 않는다.
Kafka publish는 성공했지만 DB 상태 갱신 전에 죽었을 수 있기 때문이다.
이 경우 같은 dispatch message가 다시 발행될 수 있고, worker의 `notificationId` 기준 멱등과 `PUBLISHED|RETRY_WAIT -> PROCESSING` claim이 중복 발송을 막는다.

### 늦은 publish 결과 처리

stale 회수 후 이전 publisher의 Kafka callback이 늦게 도착할 수 있다.
publish 결과 반영은 outbox가 아직 `PUBLISHING`일 때만 수행한다.

| 현재 Outbox 상태 | 늦은 publish 성공/실패 callback 처리 |
| --- | --- |
| `PUBLISHING` | 정상 callback으로 보고 `PUBLISHED` 또는 `PENDING/DEAD` 반영 |
| `PENDING` | stale 회수 이후 도착한 callback으로 보고 무시 |
| `PUBLISHED` | 이미 terminal 성공이므로 무시 |
| `DEAD` | 이미 terminal 실패이므로 무시 |

이 규칙은 stale 회수와 늦은 callback이 같은 outbox를 서로 덮어쓰는 것을 막는다.

## Retry 정책

outbox retry는 dispatch message 발행 보장을 위한 정책이다.
vendor 발송 retry와 목적이 다르다.

| Retry | 실패 지점 | 상태 |
| --- | --- | --- |
| outbox retry | `notification.dispatch` Kafka publish 실패 | `NotificationOutbox.PENDING/DEAD`, `Notification.PUBLISH_FAILED` |
| dispatch retry | vendor sender 실패 | `Notification.RETRY_WAIT/DEAD`, Kafka retry topic |

outbox retry는 `RetryPolicy`의 backoff와 max attempts를 사용한다.
한도 안이면 `PENDING`으로 되돌리고 `nextRetryAt`을 갱신한다.
한도에 도달하면 `DEAD`로 전이한다.

초기 outbox publish retry는 `maxAttempts=3`, `baseDelay=PT1S`, `maxDelay=PT1M`으로 둔다.
이는 publish 실패가 3회 누적되면 `DEAD`로 전이한다는 뜻이며, 최초 실패 후 자동 재발행 기회는 최대 2번이다.
Kafka broker 장기 장애는 긴 자동 재시도로 붙잡기보다 outbox `DEAD` 운영 큐와 알람으로 전환한다.

모든 발행 실패를 retryable로 보지는 않는다.
Kafka broker 일시 장애, timeout, network 오류는 retryable 실패로 기록한다.
반면 outbox payload 역직렬화 실패, 필수 필드 누락, topic 계약 오류처럼 같은 payload를 다시 발행해도 성공하기 어려운 오류는 즉시 `DEAD`로 전이한다.
즉시 `DEAD` 처리된 outbox도 운영자가 payload와 실패 원인을 확인한 뒤 수동 복구할 수 있다.

## Scheduler 기준

기본 구현은 polling scheduler가 `publishPending()`을 주기적으로 호출하는 방식으로 둔다.
polling scheduler는 임시 구현이 아니라 outbox의 기본 발행 및 recovery 메커니즘이다.

```text
findPublishable(now, batchSize)
findStalePublishing(now - visibilityTimeout, batchSize)
claimPublishing(outboxId, publisherId, now)
publish
save result
```

한 행의 실패가 전체 batch를 깨뜨리지 않도록, adapter 구현에서는 단건 처리 경계를 분리하는 것이 좋다.
core use case는 batch 결과를 `processed`, `published`, `failed` 카운트로 반환하고, scheduler는 로그/메트릭만 남긴다.

발송 지연을 줄여야 하면 요청 저장 commit 이후 immediate publish path를 추가할 수 있다.
이 경우에도 polling scheduler는 제거하지 않는다.
immediate publish는 latency 최적화 경로이고, polling scheduler는 immediate path 누락, publish 실패, stale `PUBLISHING` 회수를 담당하는 복구 경로다.

```text
기본 경로:
  request commit
  -> outbox PENDING
  -> scheduler polling
  -> claim/publish

latency 최적화 경로:
  request commit
  -> immediate publish attempt
  -> 실패 또는 누락 시 outbox PENDING/PUBLISHING을 scheduler가 복구
```

## 운영자 복구

`NotificationOutbox.DEAD`는 자동 발행 재시도 종료 상태다.
운영자는 실패 원인을 확인한 뒤 `DEAD -> PENDING`으로 복구할 수 있다.

복구된 outbox는 다음 scheduler tick에서 다시 claim/publish 대상이 된다.

복구 시 필드는 다음처럼 초기화한다.

| 필드 | 복구 후 값 |
| --- | --- |
| `outboxStatus` | `PENDING` |
| `retryCount` | `0` |
| `nextRetryAt` | `now` |
| `lastError` | `null` |
| `publishedAt` | `null` |
| `claimedAt` | `null` |
| `claimedBy` | `null` |

복구는 자동 재발행이 아니라 재발행 대기열로 되돌리는 조치다.
실제 Kafka publish는 다음 scheduler tick의 claim/publish 흐름을 다시 탄다.

## 트레이드오프

### 장점

- 요청 저장과 dispatch 발행 대기열 저장 사이의 유실을 막는다.
- Kafka publish 실패와 vendor 발송 실패를 서로 다른 상태로 추적할 수 있다.
- 다중 service 인스턴스에서도 outbox claim으로 중복 publish를 줄일 수 있다.
- stale publishing 회수로 publisher 중단 후 멈춘 row를 자동 복구할 수 있다.

### 단점

- outbox table과 scheduler가 필요하다.
- publish 성공 후 상태 저장 전 장애는 중복 publish로 이어질 수 있으므로 worker 멱등이 필수다.
- outbox `DEAD` 복구를 위한 admin 기능이 필요하다.
