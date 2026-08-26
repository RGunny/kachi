# 026. 키워드 identity와 구독, 채널 바인딩

## 배경

ADR 025는 "누가 어떤 키워드를 어떤 채널로 받는가"를 user-service가 소유하기로 했다.  
이 ADR은 그 결정을 user-service 안에서 어떻게 구현하는지 정한다.

지금 user-service의 `Keyword`는 사용자별 aggregate다.  
`Keyword(userId, name)`이 `(user_id, name)` unique로 저장되고, 활성 키워드 internal API는 이름 문자열을 대소문자를 구분한 채 중복 제거해 내보낸다.   
`tesla`, `Tesla`, ` TESLA `는 세 키워드이고, collector와 ai는 세 번 수집하고 세 번 요약한다. 사용자의 알림 채널과 주소는 아직 없는 정보이다.  

정해야 할 것은 다음이다.  

1. 사용자별 `Keyword`와 ADR 025의 canonical 키워드·구독의 관계. 기존 aggregate를 어떻게 바꾸는가.
2. 뒷단(collector·ai·routing)에서 사용하는 키워드 문자열을 활성 키워드 API가 어떤 값으로 내보내는가.
3. 채널 바인딩의 생명주기와 주소 저장 방식. Telegram처럼 사용자가 주소를 직접 입력할 수 없는 채널의 연결 절차.
4. internal API 3개의 계약과 보안 경계.

## 결정

### canonical 키워드와 구독

`Keyword`를 **canonical 키워드**로 재정의한다.  
사용자와 무관하게 정규화 값(`canonicalKey`) 하나에 행 하나이며, 최초 등록 원문을 `displayName`으로 보관한다.  
사용자별 역할은 새 aggregate `Subscription(userId, keywordId, channels, enabled)`이 승계한다.  
기존 `keywords` 테이블은 `subscriptions`가 되고, canonical `keywords`는 새로 만든다.

```
사용자 A ─ Subscription(A, K, [SLACK])   ─┐
                                        ├─ Keyword K (canonicalKey = "tesla", displayName = "Tesla")
사용자 B ─ Subscription(B, K, [TELEGRAM]) ┘
```

ADR 025가 활성 키워드 API에 `keywordId`를 처음부터 싣기로 한 이유는 나중에 이름 변경·병합의 identity로 옮기기 위해서다.  
사용자별 `Keyword`에 `canonical_key` 컬럼만 더하는 방식은 코드 변경이 가장 작지만 `keywordId`가 사용자마다 달라 그 목적에 맞지 않는다.  
canonical 행을 지금 두면 identity가 처음부터 안정적이고, 그 대가는 기존 aggregate 하나의 이름과 역할이 바뀌는 것이다.  

정규화 규칙은 ADR 025 그대로다.  
"양끝 구두점"은 Unicode 일반 카테고리 P*(구두점)에 속하는 문자이고, 기호(S*)는 남긴다.  
`"SPACE-X"`의 중간 하이픈과 `"$TSLA"`의 `$`는 유지되고 `"테슬라."`의 마침표는 제거된다.  
정규화 결과가 비면 거부한다.

구독의 unique 키는 `(userId, keywordId)`다.  
canonical 행은 `canonicalKey`당 하나이므로 이는 ADR 025의 `(userId, canonicalKey)`와 같은 키다.  
`canonical_key` 컬럼은 `utf8mb4_bin`으로 둔다.
대소문자를 무시하는 collation이면 DB의 동등성이 코드의 정규화보다 느슨해져 중복 판정이 두 곳으로 갈라진다.  
같은 사용자가 `Tesla`를 등록한 뒤 `tesla`를 등록하면 중복이다.  
구독은 채널을 하나 이상 가지며, 등록·변경 시점에 각 채널의 바인딩이 `ACTIVE`여야 한다.  
바인딩이 나중에 해지되면 구독은 그대로 두고 internal 조회가 그 채널을 걸러낸다.  
해지는 사용자 행위이고, 다시 연결하면 구독이 그대로 살아나야 한다.

키워드 이름 변경 API는 없앤다. 이름이 다르면 다른 키워드다(ADR 025).

### 활성 키워드 API의 `name`은 canonicalKey다

뒷단 join key는 정규화된 문자열이다(ADR 025).  
collector와 ai는 응답의 `name`만 읽어 그 문자열로 뉴스를 수집·저장하고, ai는 그 값을 이벤트의 `keyword`로 발행한다.  
routing은 그 `keyword`로 `GET /internal/subscriptions?keyword=`를 묻는다.  
`name`이 원문이면 routing이 조회 전에 정규화를 한 번 더 해야 하고, 두 서비스가 같은 normalize 구현을 가져야 한다.  
그래서 `name`에 canonicalKey를 보낸다. 응답에는 `keywordId`, `canonicalKey`, `displayName`을 함께 싣는다.  
collector·ai의 클라이언트 코드는 바뀌지 않는다(추가 필드는 무시된다).

대가는 수집·요약이 소문자 정규화 문자열로 돈다는 것이다.  
검색 provider는 대소문자를 구분하지 않고, LLM 프롬프트의 키워드도 마찬가지라 결과 품질에 영향은 없다고 본다.  
사람에게 보이는 이름이 필요한 곳(알림 본문)은 routing이 `displayName`을 쓴다.

### 채널 바인딩

`ChannelBinding(userId, channel, address, status)`은 사용자·채널당 하나다. 그 id가 ADR 025의 `recipientRef`다.

| 상태 | 뜻 |
| --- | --- |
| `PENDING` | Telegram 연결 토큰을 발급했고 chat id가 아직 없다 |
| `ACTIVE` | 주소가 있고 발송 가능하다 |
| `REVOKED` | 사용자가 해지했다. 주소는 지운다 |

- Slack·Discord는 사용자가 자기 incoming webhook URL을 등록한다. URL은 채널별 host prefix(`https://hooks.slack.com/`, `https://discord.com/api/webhooks/`)를 검증한다. worker가 이 URL로 HTTP를 보내므로 임의 host를 받으면 안 된다.
- Telegram은 사용자가 chat id를 모른다. 연결 토큰(무작위 32바이트, 10분 만료, 해시만 저장)을 발급해 `https://t.me/{bot}?start={token}` 링크를 돌려주고, 봇이 `/start <token>`을 받으면 `POST /internal/channel-bindings/telegram/link {token, chatId}`로 바인딩이 `ACTIVE`가 된다. 봇 업데이트를 받는 쪽은 Telegram API를 호출하는 notification-worker의 관심사라 이 ADR 범위 밖이다.
- 재등록은 새 행이 아니라 기존 행 갱신이다. `recipientRef`가 바뀌지 않아 진행 중인 알림이 새 주소로 간다.
- 해지는 주소를 null로 지운다. 보관할 이유가 없다.

주소는 AES-256-GCM으로 암호화해 저장한다(Spring Security `AesBytesEncryptor`, 키는 env `KACHI_USER_BINDING_KEY`). 행마다 `key_version`을 두어 키 교체를 행 단위로 진행할 수 있게 하되, 교체 절차 자체는 후속이다. 도메인의 `ChannelAddress`는 `toString()`이 마스킹된 값을 돌려준다. 로그에 주소가 남는 경로를 타입에서 막는다.

### internal API

| API | 응답 | 소비자 |
| --- | --- | --- |
| `GET /internal/keywords/active` | `[{keywordId, canonicalKey, displayName, name}]` — enabled 구독이 하나 이상인 키워드 | collector, ai |
| `GET /internal/subscriptions?keyword=` | `[{userId, channel, recipientRef}]` — enabled 구독 x 채널 중 바인딩 ACTIVE만 | notification routing (R1) |
| `GET /internal/channel-bindings/{ref}` | `{channel, status, address?}` — ACTIVE일 때만 복호화 주소 | notification-worker (W1) |
| `POST /internal/channel-bindings/telegram/link` | 204 | Telegram 봇 수신기 (후속) |

internal API는 인증을 두지 않고 `/api/v1/internal/**`를 `permitAll`로 연다. 지금 이 API들은 같은 호스트의 서비스끼리만 호출하고 외부 ingress가 없다. 구독 조회는 userId를, 바인딩 조회는 복호화한 주소를 돌려주므로 외부에 노출되면 안 되는 API이고, **ingress를 붙이는 시점이 공유 토큰 인증(`X-Internal-Token` 필터 + 호출자 세 곳의 헤더)을 넣는 단계의 착수 조건**이다. 지금 넣지 않는 이유는 호출자 collector·ai를 같은 PR에서 바꿔야 해 브랜치가 모듈 하나를 넘기 때문이다.

## 검토한 대안

- **사용자별 `Keyword`에 `canonical_key` 컬럼 추가**
  - 변경이 가장 작지만 `keywordId`가 사용자마다 달라 이름 변경·병합의 identity가 되지 못한다.
- **활성 키워드 API의 `name`을 원문으로 두고 routing이 정규화**
  - normalize 구현이 두 서비스에 생긴다. 하나가 바뀌면 조회가 조용히 빈 결과를 낸다.
- **바인딩을 채널당 여러 행, 해지 행 보존**
  - `recipientRef` 교체 규칙과 REVOKED 행 정리가 필요하다. 사용자·채널당 하나면 둘 다 없다.
- **주소를 평문 저장**
  - webhook URL은 그 자체로 발송 권한이다. DB dump가 곧 발송 권한 유출이다.
- **KMS·envelope 암호화**
  - 키 하나를 env로 받는 것과 비교해 지금 규모에서 얻는 것이 없다. `key_version`으로 교체 경로만 남긴다.
- **internal API 공유 토큰을 지금**
  - 호출자 두 모듈을 같이 바꿔야 한다. 착수 조건(ingress)을 적어 두는 것으로 대신한다.
- **Java 마이그레이션으로 기존 키워드를 정규화해 이관**
  - 이관할 데이터가 없는 단계에서 정규화 로직을 마이그레이션에 복제하는 비용만 남는다.

## 트레이드오프

- 장점:
  - 같은 키워드를 몇 명이 등록해도 canonical 행은 하나라 수집·요약이 한 번이다.
  - `keywordId`가 처음부터 사용자와 무관한 identity다.
  - 주소가 DB에 평문으로 없고, 이벤트·로그에는 `recipientRef`만 남는다.
  - collector·ai 클라이언트는 바뀌지 않는다.
- 단점:
  - 기존 `Keyword` aggregate의 이름과 역할이 바뀐다. 이름 변경 API가 사라진다.
  - 뒷단이 다루는 키워드 문자열이 소문자 정규화 값이다.
  - 암호화 키 하나가 운영 비밀에 추가된다.
  - Telegram 연결은 봇 수신기가 붙기 전까지 `PENDING`에서 끝난다.

## 후속

- Telegram 봇 업데이트 수신기 (`/start` → link internal API)
- internal API 공유 토큰 — ingress 도입 시
- 암호화 키 교체 절차
- Telegram 403 → 바인딩 `BOUNCED`
- 운영자 alias 표 (ADR 025)
