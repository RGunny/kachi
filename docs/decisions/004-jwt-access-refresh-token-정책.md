# 004. JWT Access/Refresh Token 정책

## 배경

`user-service`는 OAuth2 로그인 이후 클라이언트가 API를 호출할 수 있도록 인증 토큰을 발급해야 한다.

서버 세션을 사용하면 구현은 단순하지만, user-service를 여러 인스턴스로 확장할 때 세션 공유 또는 sticky session 등이 필요하다.

API 서버가 아무것도 저장하지않도록 stateless하게 운영하는 방향을 기본으로 잡고 있으므로 JWT 기반 인증을 사용한다.
토큰 자체에 정보를 담고, 서명으로 위변조만 검증/방어 한다.
서명은 내용을 숨기는 것이 아닌, 내용이 변조되지 않았음을 증명한다.

다만 JWT는 한 번 발급되면 만료 전까지 서버가 직접 회수하기 어렵다.
따라서 짧게 사는 access token과 길게 사는 refresh token을 분리한다.

## 결정

`user-service`는 access token과 refresh token을 분리해서 발급한다.

| 토큰 | 만료 시간 | 용도 |
| --- | --- | --- |
| Access Token | 15분 | API 요청 인증 |
| Refresh Token | 14일 | access token 재발급 |

JWT secret은 운영 환경에서 `KACHI_JWT_SECRET` 환경변수로 주입한다.

```yaml
kachi:
  jwt:
    secret: ${KACHI_JWT_SECRET}
    access-token-ttl: 15m
    refresh-token-ttl: 14d
```

로컬 개발 환경에서는 `application-local.yaml`에서만 기본 secret을 제공한다.
운영 공통 설정에는 기본 secret을 두지 않는다.

secret은 HMAC 서명용 key material이며 hash가 아니다.
로컬 또는 운영 secret은 다음처럼 32 byte 이상 랜덤 값을 Base64로 생성한다.

```sh
openssl rand -base64 32
```

JWT claim은 최소한만 넣는다.

| Claim | Access Token | Refresh Token | 설명 |
| --- | --- | --- | --- |
| `jti` | 사용 | 사용 | 토큰 자체의 고유 식별자 |
| `sub` | 사용 | 사용 | `UserId` |
| `type` | `ACCESS` | `REFRESH` | 토큰 용도 구분 |
| `role` | 사용 | 미사용 | access token 인가 판단용 |

Refresh token은 `jti` 기준으로 Redis에 저장하고, 재발급 시 기존 token을 새 token으로 회전한다.

```text
refresh_token:{userId}:{jti} -> "1"
```

저장 값은 의미 있는 객체가 아니라 존재 여부 확인용 문자열로 둔다.
TTL은 refresh token 만료 시간과 동일하게 둔다.

## 이유

### Access Token 15분

Access token은 API 요청마다 사용된다.
너무 길게 잡으면 탈취 시 피해 시간이 길어지고, 너무 짧게 잡으면 재발급 요청이 지나치게 잦아진다.

15분은 다음 균형을 위한 초기값이다.

- 토큰 탈취 시 노출 시간을 제한한다.
- 일반적인 사용자 세션에서 재발급 빈도를 과도하게 높이지 않는다.
- refresh token을 통해 사용자는 로그인 상태를 유지할 수 있다.
- 추후 실제 사용 패턴과 보안 요구 수준에 따라 5분, 10분, 30분 등으로 조정 가능하다.

### Refresh Token 14일

Refresh token은 사용자가 로그인 상태를 유지하는 기간을 결정한다.
너무 짧으면 사용자가 자주 다시 로그인해야 하고, 너무 길면 탈취 또는 방치된 기기에서 위험이 커진다.

14일은 다음 균형을 위한 초기값이다.

- 개인 서비스에서 재로그인 부담을 낮춘다.
- 30일 이상 장기 토큰보다 노출 기간을 줄인다.
- Redis 저장소에 저장해 재발급, 회수, 재로그아웃 정책을 붙이기 쉽다.
- 추후 “remember me” 옵션이 필요하면 기본 refresh token과 장기 refresh token을 분리할 수 있다.

### Claim 최소화

JWT payload는 클라이언트에서 디코딩 가능하다.
서명은 위변조를 막지만 내용을 숨기지는 않는다.

따라서 이메일, 닉네임, OAuth provider 같은 개인정보나 변경 가능성이 큰 값은 넣지 않는다.
API 인증과 기본 인가에 필요한 `sub`, `type`, `role`, 토큰 추적에 필요한 `jti`만 넣는다.

### 토큰 식별자

`sub`는 토큰이 어떤 사용자의 것인지 나타내고, `jti`는 발급된 토큰 자체를 구분한다.
같은 사용자가 여러 기기나 브라우저에서 로그인하면 `sub`는 같지만 refresh token은 각각 별도로 회수되어야 한다.

따라서 access token과 refresh token 모두 `jti`를 가진다.
특히 refresh token은 이후 저장소에 `jti`를 저장해 다음 정책을 구현할 수 있다.

- 특정 refresh token만 폐기한다.
- refresh token 재발급 시 기존 token을 폐기하고 새 token으로 회전한다.
- 이미 사용되었거나 폐기된 refresh token 재사용을 차단한다.

### Refresh Token 저장소

Refresh token 저장소는 Redis를 사용한다.
Redis는 TTL 기반 만료와 key 존재 여부 확인이 단순하고, refresh token처럼 수명이 있는 인증 보조 데이터에 적합하다.

key에는 `userId`와 `jti`를 모두 포함한다.
`userId`만 key로 쓰면 여러 기기 동시 로그인을 표현하기 어렵고, `jti`만 쓰면 사용자 단위 전체 로그아웃 같은 정책을 확장하기 어렵다.

재발급 시에는 Lua script로 기존 key 삭제와 새 key 저장을 한 번에 수행한다.
분리된 `DEL`/`SET` 호출은 중간에 다른 요청이 끼어들 수 있으므로, Redis 서버에서 script를 원자적으로 실행해 token rotation 경쟁 조건을 줄인다.

## 결과

- user-service는 서버 세션 없이 API 인증을 처리할 수 있다.
- Access token 만료 시간이 짧아 refresh token 재발급 흐름이 필요하다.
- Refresh token은 Redis 저장소에 존재해야 재발급에 사용할 수 있다.
- Refresh token 재발급 시 기존 refresh token은 폐기되고 새 refresh token으로 교체된다.
- OAuth 로그인처럼 refresh token을 최초 발급하는 흐름은 클라이언트에 응답하기 전에 refresh token `jti`를 Redis에 저장해야 한다.
- JWT secret은 반드시 운영 환경변수로 private 하게 관리해야 한다.
- 토큰 claim이 최소화되어 사용자 정보가 필요할 때는 서버 저장소 조회가 필요하다.
