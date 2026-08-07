# User 컨텍스트 도메인 모델

user-service의 도메인 모델이다. 사용자 인증·상태와 관심 키워드 관리를 담당한다.
공통 관례(생성/복원 분리, id VO, Clock 주입)는 [도메인모델.md](도메인모델.md)를 따른다.

## 사용자 애그리거트

### 사용자(User)

_Aggregate Root_

#### 속성(Attributes)

- `id`: `UserId` 사용자 식별자
- `email`: `Email` 로그인 이메일 - Natural ID
- `nickname`: `Nickname` 사용자 표시 이름
- `status`: `UserStatus` 사용자 상태
- `role`: `UserRole` 사용자 권한
- `authProvider`: `AuthProvider` 인증 제공자
- `providerUserId`: `ProviderUserId?` OAuth provider가 제공하는 사용자 식별자
- `registeredAt`: 가입 일시
- `lastLoginAt`: 마지막 로그인 일시
- `deactivatedAt`: 탈퇴 일시

#### 행위(Behaviors)

- `static register(email, nickname, authProvider, providerUserId, registeredAt)`: 사용자를 `ACTIVE` 상태로 등록한다
- `static restore(...)`: 저장소 snapshot을 도메인 객체로 복원한다
- `activate()`: 사용자를 활성화하고 탈퇴 일시를 지운다
- `deactivate(deactivatedAt)`: 사용자를 `DELETED` 상태로 전이하고 탈퇴 일시를 기록한다
- `recordLogin(loggedInAt)`: 로그인 성공 시각을 기록한다

#### 규칙(Rules)

- 이메일은 중복될 수 없다 (저장소 unique 제약으로 방어한다).
- `LOCAL` 사용자는 provider user id를 가질 수 없고, OAuth 사용자는 반드시 가져야 한다.
- 탈퇴(`DELETED`)한 사용자는 활성화할 수 없다.
- 이미 탈퇴한 사용자는 다시 탈퇴할 수 없다.
- 활성(`ACTIVE`) 사용자만 로그인 시각을 기록할 수 있다.
- 활성 사용자만 키워드를 등록·수정·삭제할 수 있다 (application 계층에서 강제한다).

### 사용자 상태(UserStatus)

_Enum_

- `ACTIVE`: 활성
- `INACTIVE`: 휴면
- `DELETED`: 탈퇴

### 사용자 권한(UserRole)

_Enum_

- `USER`: 일반 사용자
- `ADMIN`: 관리자

### 인증 제공자(AuthProvider)

_Enum_

- `LOCAL`, `GOOGLE`, `NAVER`, `KAKAO`

### 사용자 식별자(UserId)

_Value Object_

- `value`: 사용자 식별 UUID
- `newId()` / `of()`: 신규 생성(UUID v7) / 저장소 값 복원

### 이메일(Email)

_Value Object_

- `value`: 이메일 주소
- `of()`: trim 후 소문자로 정규화한다. 빈 값 불가, `@` 포함 필수, 255자 이하.

### 닉네임(Nickname)

_Value Object_

- `value`: 사용자 표시 이름
- `of()`: trim 정규화. 빈 값 불가, 100자 이하.

### OAuth 사용자 식별자(ProviderUserId)

_Value Object_

- `value`: OAuth provider가 발급한 사용자 식별 문자열
- `of()`: trim 정규화. 빈 값 불가, 255자 이하.

## 키워드 애그리거트

### 키워드(Keyword)

_Aggregate Root_

독립적으로 저장·조회되는 자체 aggregate이며, 소유자는 `UserId`로 참조한다
(aggregate 간 참조는 객체가 아니라 id로 한다).

#### 속성(Attributes)

- `id`: `KeywordId` 키워드 식별자
- `userId`: `UserId` 키워드를 소유한 사용자 식별자
- `name`: `KeywordName` 키워드 이름
- `enabled`: 수집 대상 포함 여부
- `registeredAt`: 등록 일시
- `disabledAt`: 비활성화 일시

#### 행위(Behaviors)

- `static create(userId, name, registeredAt)`: 키워드를 `enabled=true`로 등록한다
- `static restore(...)`: 저장소 snapshot을 복원한다
- `rename(name)`: 키워드 이름을 변경한다
- `enable()`: 수집 대상으로 포함하고 비활성화 일시를 지운다
- `disable(disabledAt)`: 수집 대상에서 제외하고 비활성화 일시를 기록한다

#### 규칙(Rules)

- 키워드는 반드시 한 명의 사용자에게 속한다.
- 한 사용자는 같은 이름의 키워드를 중복 등록할 수 없다 (저장소 unique 제약으로 방어한다).
- 이미 비활성화된 키워드는 다시 비활성화할 수 없다.
- 비활성 키워드는 뉴스 수집 대상에서 제외된다 (활성 키워드 조회 API가 걸러낸다).

### 키워드 식별자(KeywordId)

_Value Object_

- `value`: 키워드 식별 UUID
- `newId()` / `of()`

### 키워드 이름(KeywordName)

_Value Object_

- `value`: 키워드 이름
- `of()`: trim 정규화. 빈 값 불가, 100자 이하.
