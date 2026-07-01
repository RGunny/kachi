# 016. notification-worker vendor sender 구조와 설정 구성

## 배경

`notification-worker`는 `notification.dispatch` 메시지를 consume한 뒤 외부 vendor로 실제 알림을 발송한다.

초기 실제 sender 대상은 다음 세 채널이다.

- Slack incoming webhook
- Discord incoming webhook
- Telegram Bot API `sendMessage`

세 vendor는 모두 HTTP 기반이지만 인증 방식, request shape, success response, rate limit 표현, 실패 body 구조가 다르다.
이 차이를 `notification-core` 또는 공통 sender 추상화로 끌어올리면 vendor별 HTTP 의미가 섞이고, 반대로 sender 한 클래스에 WebClient 호출, JSON DTO, 실패 분류를 모두 넣으면 sender가 길어져 유지보수가 어려워진다.

## 서비스 사용성 전제

휴대폰 번호, 이메일을 사용하는 문자/이메일과는 달리, 
Slack/Discord webhook과 Telegram bot 발송은 사용자가 개인 알림 채널을 직접 등록해서 쓰는 모델에는 적합하지 않다.
webhook URL, bot token, channel/chat id 같은 값은 일반 사용자가 발급 및 관리하기 어렵다.

사실 kachi 프로젝트 자체가 내가 사용하고 싶은 기능, 내가 설계/구현 해보고 싶은 기술 적용을 위함이 크다. 
특히, AI 모델, 뉴스API 등 외부API에 대한 의존과 비용 문제로 지인 외 외부사용자에게 실서비스로 오픈할 계획은 없었다.
따라서, 프로젝트 완료 후 개인적으로 사용 및 유지보수해가다 실서비스로 확장에 대한 마음이 생기면 그 때 방향성을 고민하겠다.
단, 언제든 쉽게 현 프로젝트의 구조를 흔들지 않으며 외부 vendor를 확장하기 쉽도록 설계를 튼튼히 하는 것이 중요하겠다.

현재 세 vendor의 용도를 생각한다면 다음과 같은 방향성이 떠오른다. 

- 웹, Discord 채널, Telegram 채팅방처럼 운영자가 관리하는 공간으로 알림을 보낸다.
- 키워드나 관심 주제를 사용자별로 즉시 발송하기보다, 투표나 운영 기준으로 모은 키워드에 대해 알람한다.
- 시황 요약, 일일 리포트, 운영 공지처럼 개인 수신함보다 채널 기반 알림에 어울리는 메시지를 검증한다.
  - 이부분은 집계, 배치 등을 활용하여 추가 가능하며 후순위 개발로 둔다.

SMS와 Email은 초기 실제 발송 대상에서 제외한다.
SMS는 발송 비용과 provider 계약이 필요하고, Email은 도메인, SPF/DKIM/DMARC, bounce 처리, reputation 관리 등 운영 인프라가 커진다.
따라서 초기에는 실제 외부 발송 경험을 확인하기 쉬운 앱 기반 채널로 Slack/Discord/Telegram을 먼저 구현한다.

현재 서비스는 AI 비용 때문에 지인 외 일반 사용자에게 넓게 오픈하는 것을 전제로 하지 않는다.
다만 notification-core는 `NotificationSender` port로 채널 발송을 추상화하고, worker는 vendor별 sender를 adapter로 구현한다.
이 구조를 유지하면 이후 SMS, Email, push, 사용자별 preference, 사용자 credential 관리가 필요해질 때 core 모델을 크게 흔들지 않고 sender와 설정을 확장할 수 있다.

## 결정

vendor sender는 다음 구조로 구현한다.

```text
adapter/outbound/sender/{vendor}
  {Vendor}NotificationSender
  {Vendor}{Operation}Client
  dto/
    request/response/result DTO
```

예시:

```text
slack/
  SlackNotificationSender
  SlackWebhookClient
  dto/
    SlackWebhookRequest
    SlackWebhookResult

discord/
  DiscordNotificationSender
  DiscordWebhookClient
  dto/
    DiscordWebhookRequest
    DiscordWebhookErrorResponse
    DiscordWebhookResult

telegram/
  TelegramNotificationSender
  TelegramSendMessageClient
  dto/
    TelegramSendMessageRequest
    TelegramApiResponse
    TelegramResponseParameters
    TelegramSendMessageResult
```

## 책임 분리

### Sender

`{Vendor}NotificationSender`는 `notification-core`의 `NotificationSender` port 구현체다.

책임:

- 지원 channel 판단
- `SendNotificationCommand` 기본 검증
- client 호출
- WebClient request exception과 timeout 분류
- client 결과를 `SendNotificationResult`로 변환
- vendor별 HTTP status/body 의미를 core 표준 실패 모델로 매핑

포함하지 않을 것:

- WebClient request body 조립
- vendor JSON DTO 파싱
- header/body에서 retry-after 추출
- vendor API URL/path 조립

### Client

`{Vendor}{Operation}Client`는 vendor HTTP 계약을 호출하고 sender의 실패 매핑에 필요한 client output DTO를 반환한다.
client output DTO는 외부 API 응답 DTO가 아니라, sender가 `SendNotificationResult`를 만들 때 필요한 HTTP status, retry-after, vendor 오류 정보를 담는 내부 전달 타입이다.

현재 타입은 다음과 같다.

- Slack: `SlackWebhookResult`
- Discord: `DiscordWebhookResult`
- Telegram: `TelegramSendMessageResult`

책임:

- WebClient 호출
- URI/path 조립
- request DTO 생성
- response DTO 파싱
- vendor retry-after 값을 millis로 변환
- sender 실패 매핑에 필요한 client output DTO 생성

client는 notification-core port가 아니다.
worker adapter 내부 구현 세부사항이므로 Spring bean으로 직접 노출하지 않고, config에서 sender 조립 시 생성한다.

### dto 패키지

`dto` 패키지는 해당 vendor HTTP operation의 데이터 전달 타입을 모은다.

포함 대상:

- vendor JSON request DTO
- vendor JSON response DTO
- client가 sender에 전달하는 result DTO

result DTO는 외부 JSON DTO는 아니지만, 같은 vendor HTTP operation의 데이터 전달 타입을 한 패키지에서 찾을 수 있도록 `dto`에 둔다.
각 result DTO 주석에는 외부 JSON DTO가 아니라 client-output contract이라는 점을 명시한다.

## 설정 구성

vendor별 설정은 다음 prefix를 사용한다.

```text
kachi.notification.worker.sender.slack
kachi.notification.worker.sender.discord
kachi.notification.worker.sender.telegram
```

현재 구성은 non-secret 값과 secret 값을 분리한다.

- `application.yaml`: sender `enabled`, timeout, buffer, Telegram base URL/path
- `application-local.yaml`: Slack/Discord webhook URL, Telegram bot token env 연결
- `.env.example`, `.env`, `.env.local`: 실제 로컬 실행과 real integration test에 필요한 env key

`application.yaml`의 sender 설정은 다음 형태다.

```yaml
kachi:
  notification:
    worker:
      sender:
        slack:
          enabled: true
        discord:
          enabled: true
        telegram:
          enabled: true
```

`application-local.yaml`은 secret 값을 env placeholder로 연결한다.

```yaml
kachi:
  notification:
    worker:
      sender:
        slack:
          webhook-url: ${KACHI_NOTIFICATION_SLACK_WEBHOOK_URL:}
        discord:
          webhook-url: ${KACHI_NOTIFICATION_DISCORD_WEBHOOK_URL:}
        telegram:
          bot-token: ${KACHI_NOTIFICATION_TELEGRAM_BOT_TOKEN:}
```

각 real sender bean은 자기 `enabled` 값을 보고 등록된다.
mock sender가 같은 channel을 포함하고 있어도 real sender가 enabled인 channel은 mock sender 지원 목록에서 제외된다.
따라서 local 설정에서 mock sender를 켜둔 채 Slack/Discord/Telegram 실제 sender도 함께 켜서, 실제 sender가 붙은 channel만 real path로 검증할 수 있다.

## WebClient 구성

vendor별 전용 WebClient를 둔다.

각 WebClient는 다음 값을 vendor별 property에서 받는다.

- `connect-timeout`
- `response-timeout`
- `read-timeout`
- `write-timeout`
- `max-in-memory-size`

설정값은 모두 양수 검증을 한다.
sender 내부 `.timeout()`으로 처리하지 않고 Reactor Netty `HttpClient`에 timeout을 설정한다.

공용 WebClient를 쓰지 않는 이유:

- vendor별 장애/지연 특성이 다르다.
- response size와 timeout을 독립적으로 조정해야 한다.
- vendor별 실험이나 운영 튜닝을 다른 sender에 전파하지 않아야 한다.

## 실패 분류

공통화는 HTTP client 계층의 timeout cause-chain 분류까지만 한다.

```text
VendorHttpExceptionClassifier
  -> SocketTimeoutException
  -> ConnectTimeoutException
  -> ReadTimeoutException
  -> WriteTimeoutException
  -> TimeoutException
```

HTTP status, vendor body, failure message, auth/validation/rate-limit 판단은 각 sender에 둔다.

### Slack

- success: 2xx
- rate limit: 429 + `Retry-After`
- transient: 5xx
- authorization: 401/403
- permanent validation: 그 외 4xx

### Discord

- success: 2xx, 특히 incoming webhook의 204 No Content
- rate limit: 429
- retry-after 우선순위:
  1. `Retry-After`
  2. `X-RateLimit-Reset-After`
  3. body `retry_after`
- transient: 5xx
- authorization: 401/403
- permanent validation: 그 외 4xx

Discord는 204 응답이나 일부 오류 응답이 JSON content type이 아닐 수 있다.
따라서 client는 모든 응답 body를 DTO로 파싱하지 않고, 429이면서 header retry-after가 없을 때만 body fallback을 읽는다.

### Telegram

- API: `POST /bot{token}/sendMessage`
- request: `chat_id`, `text`
- success: HTTP 2xx + body `ok=true`
- rate limit: HTTP 429 또는 body `error_code=429`
- retry-after: body `parameters.retry_after`
- authorization: HTTP/body 401/403
- validation: 잘못된 `chat_id`, message length 초과, 일반 4xx

Telegram `retry_after`는 초 단위이고, core 실패 모델은 millis를 사용하므로 client에서 millis로 변환한다.

## Mock sender와 real sender 중복 방지

mock sender는 초기 dispatch flow 검증용이다.
실제 sender가 enabled인 channel은 mock sender 지원 channel에서 제외한다.

```text
configuredMockChannels - enabledRealSenderChannels
```

이 규칙이 없으면 같은 channel을 지원하는 sender가 둘 이상 등록되어 `NotificationSenderRouter`가 routing ambiguity를 만난다.

## 테스트 기준

vendor sender는 다음 테스트를 갖는다.

- sender unit test
  - supports channel
  - success
  - rate limit
  - transient 5xx
  - authorization failure
  - validation/permanent failure
- config test
  - WebClient 생성
  - sender 생성
  - secret blank/null 검증
  - timeout/buffer invalid 검증
- real integration test
  - env 값이 있을 때 실제 vendor로 발송
  - env 값이 없으면 JUnit assumption으로 skip

real integration test는 다음 env를 사용한다.

```text
KACHI_NOTIFICATION_SLACK_WEBHOOK_URL
KACHI_NOTIFICATION_DISCORD_WEBHOOK_URL
KACHI_NOTIFICATION_TELEGRAM_BOT_TOKEN
KACHI_NOTIFICATION_TELEGRAM_CHAT_ID
```

## 결과

- `notification-core`는 vendor HTTP 계약을 알지 않는다.
- sender는 core 표준 `SendNotificationResult` 변환 책임에 집중한다.
- client는 vendor HTTP 계약과 DTO 변환을 담당한다.
- vendor별 DTO는 한 패키지에서 찾을 수 있다.
- Slack/Discord/Telegram이 같은 구조로 확장된다.
- mock sender와 real sender를 같은 설정 안에서 조합할 수 있다.

## 트레이드오프

### 장점

- sender 핵심 로직이 짧아지고 failure classification이 읽기 쉬워진다.
- vendor HTTP 계약 변경이 client/dto에 국소화된다.
- Telegram처럼 response body 해석이 복잡한 vendor를 무리 없이 확장할 수 있다.
- Slack/Discord/Telegram의 파일 구조가 같아 신규 sender 추가 기준이 명확하다.

### 단점

- 단순 webhook sender에도 client/result 파일이 생겨 파일 수가 늘어난다.
- result DTO는 외부 JSON DTO가 아니므로 위치와 역할을 주석으로 설명해야 한다.
- 공통 추상화를 만들지 않기 때문에 vendor별 client 코드에 유사한 WebClient 호출 형태가 반복된다.

반복이 있더라도 현재는 vendor별 HTTP 계약과 failure mapping을 명확히 보존하는 것이 더 중요하다.
공통화는 timeout classifier처럼 기술적으로 동일한 영역에만 적용한다.
