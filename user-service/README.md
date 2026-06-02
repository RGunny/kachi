# user-service

사용자 인증, 사용자 상태, 관심 키워드를 관리하는 서비스다.

`collector-service`는 수집 대상 키워드를 직접 소유하지 않고, `user-service`의 internal API에서 활성 키워드를 조회한다.

## 현재 구현 상태

- `User`, `Keyword` 도메인 모델
- OAuth2 provider 기반 사용자 식별
- Google, Kakao, Naver OAuth2 사용자 정보 정규화
- JWT access token / refresh token 발급
- Redis 기반 refresh token 저장, 회전, 로그아웃
- MySQL 기반 사용자/키워드 persistence adapter
- 사용자 등록, 내 정보 조회, 탈퇴 API
- 내 관심 키워드 등록, 조회, 수정 API
- collector-service용 활성 키워드 internal API
- Spring Security 기반 JWT 인증 필터
- Actuator health endpoint

## API

현재 HTTP API는 `/api/v1` prefix를 사용한다.

### Auth

Access token 재발급:

```http
POST /api/v1/auth/token/refresh
```

```json
{
  "refreshToken": "..."
}
```

로그아웃:

```http
POST /api/v1/auth/logout
```

```json
{
  "refreshToken": "..."
}
```

로그아웃은 refresh token 저장소에서 해당 token을 제거해 추가 재발급을 막는다.

### User

사용자 등록:

```http
POST /api/v1/users
```

```json
{
  "email": "user@example.com",
  "nickname": "user",
  "authProvider": "LOCAL",
  "providerUserId": null
}
```

내 정보 조회:

```http
GET /api/v1/me
Authorization: Bearer {accessToken}
```

내 계정 탈퇴:

```http
DELETE /api/v1/me
Authorization: Bearer {accessToken}
```

### Keyword

내 키워드 목록 조회:

```http
GET /api/v1/me/keywords
Authorization: Bearer {accessToken}
```

내 키워드 등록:

```http
POST /api/v1/me/keywords
Authorization: Bearer {accessToken}
```

```json
{
  "name": "NVIDIA"
}
```

키워드 수정:

```http
PATCH /api/v1/keywords/{keywordId}
Authorization: Bearer {accessToken}
```

```json
{
  "name": "NVDA",
  "enabled": true
}
```

`enabled=false`인 키워드는 collector-service의 수집 대상에서 제외된다.

### Internal

활성 키워드 조회:

```http
GET /api/v1/internal/keywords/active
```

이 API는 collector-service가 수집 대상 키워드를 읽기 위한 내부 계약이다.
현재는 서비스 간 인증을 붙이지 않았고, public 사용자 API와 구분하기 위해 `/internal` 경로로 분리한다.

## OAuth2 로그인

OAuth2 로그인은 Spring Security OAuth2 Client 흐름을 사용한다.

```text
GET /oauth2/authorization/{registrationId}
  -> provider 인증
  -> /login/oauth2/code/{registrationId}
  -> 내부 사용자 조회/등록
  -> JWT access/refresh token 발급
  -> kachi.oauth2.success-redirect-uri 로 redirect
```

지원 provider:

- `google`
- `kakao`
- `naver`

OAuth2 authorization request는 서버 세션 대신 서명된 쿠키에 저장한다.
성공 후 token은 redirect URI fragment로 전달한다.

## 인증 정책

| 토큰 | 기본 TTL | 저장소 | 용도 |
| --- | --- | --- | --- |
| Access Token | 15분 | 저장하지 않음 | API 요청 인증 |
| Refresh Token | 14일 | Redis | access token 재발급 |

JWT claim은 `jti`, `sub`, `type`, `role` 중심으로 최소화한다.
Refresh token은 Redis에 저장되며, 재발급 시 기존 token을 폐기하고 새 token으로 회전한다.

## 저장소

`user-service`는 MySQL과 Redis를 사용한다.

MySQL:

- `users`
- `keywords`

Redis:

- `refresh_token:{userId}:{jti}`

Schema 변경은 Flyway migration으로 관리하고, JPA `ddl-auto`는 `validate`로 둔다.

## 실행

아래 명령은 repository root에서 실행한다.

로컬 인프라 실행:

```sh
docker compose -f infra/docker-compose.yml -f infra/docker-compose.mysql.yml -f infra/docker-compose.redis.yml up -d
```

로컬 환경변수 로드:

```sh
set -a
source .env.local
set +a
```

실행:

```sh
./gradlew :user-service:bootRun
```

Health check:

```sh
curl "http://localhost:8080/actuator/health"
```

테스트:

```sh
./gradlew :user-service:test
```

## 관련 문서

- [OAuth2 로그인 흐름](../docs/decisions/006-oauth2-로그인-흐름.md)
- [JWT Access/Refresh Token 정책](../docs/decisions/004-jwt-access-refresh-token-정책.md)
- [user-service에 Keyword 포함](../docs/decisions/001-user-service에-keyword-포함.md)
- [user-service 운영 기본 설정](../docs/decisions/007-user-service-운영성-기본설정.md)
