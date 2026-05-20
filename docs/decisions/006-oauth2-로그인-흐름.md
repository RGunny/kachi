# 006. OAuth2 로그인 흐름

## 배경

`user-service`는 사용자의 직접 비밀번호를 저장하지 않고 OAuth2 provider를 통해 로그인 사용자를 인증한다.
인증이 성공하면 내부 사용자 계정을 찾거나 생성하고, API 호출에 사용할 JWT access token과 refresh token을 발급한다.

OAuth2 provider마다 사용자 정보 응답 구조가 다르다.
Google은 `sub`, `email`, `name`을 평면 구조로 주지만, Kakao는 `kakao_account` 안에 email과 profile을 중첩해서 제공하고, Naver는 `response` 안에 사용자 정보를 담는다.

따라서 provider 응답 구조를 application 계층으로 직접 전달하지 않고, web inbound adapter에서 공통 사용자 정보 모델로 변환한다.

## 결정

OAuth2 로그인은 Spring Security OAuth2 Client 흐름을 사용한다.
provider별 사용자 정보 응답은 `OAuth2UserInfo` 인터페이스와 provider별 adapter로 정규화한다.

```text
GoogleOAuth2UserInfo
KakaoOAuth2UserInfo
NaverOAuth2UserInfo
        ↓
OAuth2UserInfo(providerId, email, nickname)
```

OAuth2 로그인 성공 후 흐름은 다음 순서로 처리한다.

```text
1. Spring Security가 provider에서 사용자 attributes를 받는다.
2. registrationId에 맞는 OAuth2UserInfo adapter를 선택한다.
3. authProvider + providerUserId로 기존 사용자를 조회한다.
4. 기존 사용자가 없으면 email, nickname, authProvider, providerUserId로 사용자를 등록한다.
5. IssueAuthTokensUseCase.issue(userId)를 호출한다.
6. access token과 refresh token을 클라이언트에 전달한다.
```

JWT 발급과 refresh token 저장은 OAuth2 adapter에서 직접 처리하지 않는다.
OAuth2 adapter는 로그인 성공 이벤트를 application use case로 연결하고, token 발급 정책은 `IssueAuthTokensUseCase`가 담당한다.

## 이유

### provider 응답은 adapter에서만 다룬다

OAuth2 provider 응답은 표준화되어 있지 않다.
provider별 JSON 구조, 필드명, 타입, 동의 항목 누락 가능성이 모두 다르다.

이 구조를 domain이나 application 계층까지 전달하면 특정 provider의 응답 형식이 내부 정책을 오염시킨다.
따라서 provider 응답 파싱은 `adapter.in.web.oauth` 안에서 끝내고, application 계층에는 정규화된 값만 넘긴다.

정규화 대상은 현재 다음 세 값이다.

| 값 | 설명 |
| --- | --- |
| `providerId` | provider가 제공하는 사용자 고유 식별자 |
| `email` | 사용자 로그인 이메일 |
| `nickname` | 사용자 표시 이름 |

nickname이 없으면 email 앞부분을 fallback으로 사용한다.
단, provider id와 email은 사용자 식별과 등록에 필요하므로 누락되면 로그인 실패로 처리한다.

### 사용자 식별 기준

OAuth 사용자는 email만으로 식별하지 않는다.
email은 provider 정책, 사용자 변경, 동의 범위에 따라 달라질 수 있고, 서로 다른 provider에서 같은 email을 제공할 수도 있다.

따라서 OAuth 사용자는 다음 조합으로 식별한다.

```text
authProvider + providerUserId
```

email에는 별도 unique 제약을 유지한다.
이는 같은 email로 여러 계정이 생성되는 것을 막기 위한 정책이다.
단, provider 식별과 email 중복 정책이 충돌하는 경우의 처리 방식은 사용자 경험 정책에 따라 별도 결정이 필요하다.

### token 발급 책임 분리

OAuth2 성공 handler가 직접 JWT를 만들고 Redis에 refresh token을 저장하면 인증 adapter에 token 정책이 섞인다.

token 발급과 refresh token 저장은 이미 application 계층의 `IssueAuthTokensUseCase`가 담당한다.
OAuth2 adapter는 인증 성공으로 얻은 내부 `userId`를 넘기고, 발급 결과를 redirect 또는 response로 변환하는 역할만 가진다.

### provider 추가 방식

새 provider를 추가할 때는 다음 단계를 따른다.

```text
1. AuthProvider enum에 provider 추가
2. XxxOAuth2UserInfo adapter 추가
3. OAuth2UserInfoFactory 분기 추가
4. Spring OAuth2 client registration 설정 추가
5. provider 응답 파싱 테스트 추가
```

provider별 예외를 공통 path extractor나 설정 DSL로 숨기지 않는다.
초기 단계에서는 provider별 adapter가 더 명확하고, 응답 구조 변화에 대한 테스트도 직접적이다.

### 행위 추상화 미채택

알림 발송처럼 Slack, Discord, Telegram이 모두 "메시지를 보낸다"는 같은 행위를 가진 경우에는 채널별 sender를 공통 인터페이스로 추상화하기 쉽다.

반면 OAuth2 사용자 정보 처리는 목적임 같음에도 provider별로 공통 행위가 크지 않다.
Spring Security가 이미 OAuth2 인증 요청, callback 처리, access token 교환, user info endpoint 호출을 맡는다.
애플리케이션이 직접 구현해야 하는 부분은 provider 응답에서 내부 사용자 식별에 필요한 값을 읽는 일이다.

이 값 추출은 provider마다 구조와 타입이 다르다.

| Provider | provider id | email | nickname |
| --- | --- | --- | --- |
| Google | `sub` | `email` | `name` |
| Kakao | `id` | `kakao_account.email` | `kakao_account.profile.nickname` |
| Naver | `response.id` | `response.email` | `response.nickname` 또는 `response.name` |

따라서 `loadUser()`, `fetchProfile()`, `parseAttributes()` 같은 행위 인터페이스를 provider별로 다시 만들지 않는다.
그렇게 하면 Spring Security OAuth2 Client가 이미 제공하는 흐름과 겹치고, 실제 차이는 JSON path와 fallback 규칙뿐인데도 추상 계층만 늘어난다.

현재는 공통 행위가 아니라 공통 결과만 추상화한다.

```text
OAuth2UserInfo(providerId, email, nickname)
```

즉, provider별 adapter는 서로 다른 응답 구조를 같은 내부 데이터 형태로 맞추는 역할만 가진다.

## 결과

- OAuth2 provider 응답 구조는 web inbound adapter에 격리된다.
- application 계층은 provider별 JSON 구조를 알지 않는다.
- OAuth 사용자는 `authProvider + providerUserId` 조합으로 식별한다.
- token 발급과 refresh token 저장은 `IssueAuthTokensUseCase`로 일원화한다.
- OAuth2 성공 handler는 인증 성공 후 내부 사용자 확인과 token 발급 use case 연결에 집중한다.
