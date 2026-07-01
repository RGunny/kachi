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

## DLT 기준

non-retryable 실패는 DLT로 보내지 않는다.
이미 core가 `DEAD`로 종결했기 때문에 listener는 ack하고 끝낸다.

DLT는 다음 상황을 위한 큐다.

- retryable dispatch가 Kafka retry 한도까지 실패한 경우
- listener runtime 장애처럼 core가 정상 결과를 만들지 못한 경우
- broker/serialization 문제처럼 runtime에서만 관측 가능한 실패

DLT 영속화와 운영자 재처리는 후속 admin 기능에서 다룬다.

## 트레이드오프

### 장점

- core는 Kafka를 모르고 실패 분류와 상태 전이에 집중한다.
- worker는 Kafka retry runtime 제어만 담당한다.
- vendor adapter가 늘어나도 `RetryFailure`와 `RetryPolicy` 기준으로 retry 판단을 통일할 수 있다.

### 단점

- core retry 정책과 Kafka retry topic 설정을 맞춰야 한다.
- listener가 exception을 던지는 것이 정상 제어 흐름의 일부가 된다.
- DLT 영속화/운영자 재처리 기능이 붙기 전까지는 retry 종료 후 운영 가시성이 제한된다.

## 관련 결정

Slack/Discord/Telegram sender의 client/DTO 구조와 vendor별 HTTP 실패 매핑 기준은
[016. notification-worker vendor sender 구조와 설정 구성](./016-notification-worker-vendor-sender-구조.md)을 따른다.
