# 014. notification dispatch 실패 분류와 Kafka retry 연결

## 배경

알림 발송은 외부 vendor I/O를 포함한다.
timeout, rate limit, 일시적인 5xx는 자동 재시도 대상이지만, 잘못된 recipient나 지원하지 않는 channel은 재시도해도 성공하지 않는다.

core가 Kafka를 직접 알면 application layer가 runtime 기술에 묶인다.
반대로 worker가 vendor 실패를 직접 분류하면 retry 정책이 adapter마다 흩어진다.

## 결정

`notification-core`는 sender 결과와 예외를 표준 실패 모델로 변환한다.

```text
NotificationSender
  -> SendNotificationResult
  -> RetryFailure
  -> RetryPolicy.decide()
  -> DispatchNotificationResult.failureClassification
```

Kafka retry topic으로 보낼지는 `notification-worker`가 결정한다.
worker는 core 결과를 보고 Kafka listener runtime exception으로 변환한다.

## Core 실패 모델

`RetryFailure`는 실패 코드를 단순 문자열로만 보지 않고 원천과 분류를 함께 보존한다.

| 필드 | 의미 |
| --- | --- |
| `code` | 내부 표준 코드 또는 vendor/broker 외부 코드 |
| `message` | 운영자와 로그가 볼 수 있는 실패 메시지 |
| `source` | `VENDOR`, `BROKER`, `DATABASE`, `NETWORK`, `APPLICATION` |
| `category` | `TIMEOUT`, `RATE_LIMITED`, `TRANSIENT_ERROR`, `VALIDATION_ERROR` 등 |
| `statusCode` | 외부 HTTP status code가 있을 때 보존 |
| `retryAfterMillis` | vendor가 내려준 retry-after가 있을 때 보존 |

`RetryPolicy.decide(failure, attempts)`는 다음 둘 중 하나를 반환한다.

| 결정 | 의미 |
| --- | --- |
| `Retry` | 자동 재시도 대상이며 한도에 도달하지 않음 |
| `GiveUp` | 재시도 대상이 아니거나 한도에 도달함 |

## Dispatch 결과 분류

`DispatchNotificationResult.failureClassification`은 core 밖 runtime이 ack/retry를 결정하기 위한 값이다.

| Classification | 상태 예 | worker 처리 |
| --- | --- | --- |
| `NONE` | `SENT`, 중복 skip | 정상 종료 |
| `RETRYABLE` | `RETRY_WAIT` | Kafka retry 대상 예외로 변환 |
| `NON_RETRYABLE` | `DEAD` | 정상 종료 |

중요한 규칙은 다음과 같다.

```text
RetryFailure가 정책상 retryable이어도
RetryPolicy가 GiveUp을 반환해 Notification이 DEAD가 되면
DispatchFailureClassification은 NON_RETRYABLE이다.
```

이 규칙이 없으면 이미 `DEAD` 처리된 메시지를 Kafka retry topic으로 다시 보낼 수 있다.

## Worker 예외

Kafka runtime 제어용 예외는 core가 아니라 `notification-worker`에 둔다.

예시:

```kotlin
class RetryableDispatchMessageException(
    val result: DispatchNotificationResult,
) : RuntimeException(...)
```

listener는 다음 조건에서만 예외를 던진다.

```text
result.failureClassification == RETRYABLE
```

현재 core 계약에서는 `RETRYABLE`이 `RETRY_WAIT`일 때만 내려오도록 한다.
worker는 방어적으로 `RETRY_WAIT` 상태도 함께 확인할 수 있다.

## Kafka retry 정책

Kafka retry 설정은 worker runtime 책임이다.

초기 기준:

```text
include: RetryableDispatchMessageException
exclude: NonRetryable sender/runtime exception
attempts: core DispatchNotificationPolicy.retryPolicy.maxAttempts와 맞춤
backoff: core RetryPolicy와 같은 base/max 기준 사용
dlt: notification.dispatch.dlt
```

Kafka retry attempts와 core retry attempts가 서로 다른 값을 가지면 상태와 topic 이동이 어긋난다.
따라서 설정은 같은 property에서 주입하거나, 최소한 운영 문서에서 같은 값으로 관리한다.

## PROCESSING finalize CAS 실패

worker는 `PUBLISHED` 또는 `RETRY_WAIT` 알림을 `PROCESSING`으로 claim한 뒤 외부 vendor를 호출한다.
외부 vendor 호출은 MongoDB transaction에 포함되지 않으므로, vendor 호출 이후 최종 상태 저장은 claim fencing 조건을 가져야 한다.

최종 저장은 다음 조건이 모두 맞을 때만 수행한다.

```text
notificationId 일치
status = PROCESSING
claimedAt = 내가 claim한 시각
claimedBy = 내가 claim한 worker id
```

이 조건이 맞지 않는 CAS 실패는 자동 retry 대상이 아니다.
CAS 조건 불일치는 DB 장애가 아니라 저장소가 정상적으로 "네가 기대한 상태가 아니다"라고 응답한 것이다.
같은 조건으로 다시 시도해도 조건 자체가 false이므로 성공하지 않는다.

따라서 처리 원칙은 다음과 같다.

```text
CAS 조건 불일치
  -> 같은 finalize를 재시도하지 않는다.
  -> 현재 Notification 상태를 다시 조회한다.
  -> 이미 SENT/RETRY_WAIT/DEAD 또는 다른 PROCESSING owner가 있으면 stale owner로 보고 skip한다.
```

반대로 MongoDB timeout, network error, primary election 같은 저장소 예외는 CAS 조건 불일치가 아니다.
이 경우는 저장 성공 여부가 불확실하거나 저장소가 응답하지 못한 것이므로 별도 retry 판단 대상이다.

## DLT 기준

non-retryable 실패는 DLT로 보내지 않는다.
이미 core가 `DEAD`로 종결했기 때문에 listener는 ack하고 끝낸다.

DLT는 다음 상황을 위한 큐다.

- retryable dispatch가 Kafka retry 한도까지 실패한 경우
- listener runtime 장애처럼 core가 정상 결과를 만들지 못한 경우
- broker/serialization 문제처럼 runtime에서만 관측 가능한 실패

worker는 `notification.dispatch.dlt`를 consume해 `notification_dlt_messages` collection에 영속화한다.
저장 idempotency 기준은 원본 Kafka record의 topic, partition, offset이다.
같은 DLT record가 consumer 재시작이나 ack 실패로 다시 들어와도 같은 document로 upsert한다.

DLT 운영 조회, 재처리, 폐기는 후속 admin 기능에서 다룬다.

## 트레이드오프

### 장점

- core는 Kafka를 모르고 실패 분류와 상태 전이에 집중한다.
- worker는 Kafka retry runtime 제어만 담당한다.
- vendor adapter가 늘어나도 `RetryFailure`와 `RetryPolicy` 기준으로 retry 판단을 통일할 수 있다.

### 단점

- core retry 정책과 Kafka retry topic 설정을 맞춰야 한다.
- listener가 exception을 던지는 것이 정상 제어 흐름의 일부가 된다.
- DLT 운영 조회/재처리 기능이 붙기 전까지는 영속화된 메시지를 직접 DB로 확인해야 한다.

## 관련 결정

Slack/Discord/Telegram sender의 client/DTO 구조와 vendor별 HTTP 실패 매핑 기준은
[016. notification-worker vendor sender 구조와 설정 구성](./016-notification-worker-vendor-sender-구조.md)을 따른다.
