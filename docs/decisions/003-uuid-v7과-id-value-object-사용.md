# 003. UUID v7과 ID Value Object 사용

## 상태

채택

## 배경

Kachi는 여러 aggregate를 가진다.

- `User`
- `Keyword`
- `News`
- `Summary`
- `Notification`
- `History`

각 aggregate는 고유 식별자가 필요하다. 식별자 타입을 모두 `UUID`로만 두면 구현은 단순하지만, 서로 다른 aggregate ID를 실수로 섞어도 컴파일 단계에서 잡을 수 없다.

예를 들어 `KeywordId`를 받아야 하는 곳에 `UserId`에 해당하는 `UUID`를 넘겨도 타입은 동일하게 `UUID`이기 때문에 컴파일된다.

또한 MySQL 같은 B-tree 인덱스 기반 저장소에서 완전 랜덤 UUID v4를 primary key로 사용하면 insert 위치가 분산되어 page split과 index locality 저하가 발생할 수 있다.

## 결정

Kachi의 aggregate 식별자는 UUID v7 계열의 시간 정렬 UUID를 사용한다.

신규 ID는 DB가 아니라 애플리케이션에서 생성한다.
UUID v7 생성은 `com.github.f4b6a3:uuid-creator`를 사용하고,
생성 로직은 각 ID value object의 `newId()`에 모은다.

예:

```kotlin
@JvmInline
value class UserId private constructor(
    val value: UUID
) {
    companion object {
        fun newId(): UserId = UserId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): UserId = UserId(value)
    }
}
```

또한 aggregate ID는 원시 `UUID`를 그대로 노출하지 않고 value object로 감싼다.

```text
UserId
KeywordId
NewsId
SummaryId
NotificationId
HistoryId
```

## 이유

### 후보 비교

| 후보 | 핵심 장점 | 미채택 이유 |
| --- | --- | --- |
| Long auto-increment | 8 byte라 인덱스와 조인 비용이 낮고, 단일 RDBMS에서는 가장 단순하다. | DB 시퀀스 발급에 의존한다. 다중 인스턴스, DB 분리, 서비스별 저장소 분산이 커질수록 식별자 발급 책임이 DB에 강하게 묶인다. 외부 노출 시 row 수와 발급 속도가 드러날 수 있다. |
| UUID v4 | 애플리케이션에서 분산 생성 가능하고 충돌 확률이 매우 낮다. | 완전 랜덤이라 B-tree leaf의 무작위 위치에 insert된다. page split, 단편화, cache miss가 증가할 수 있고 시간 순 정렬이나 디버깅에도 불리하다. |
| UUID v7 | 애플리케이션에서 분산 생성 가능하고, timestamp 기반이라 시간 정렬이 가능하다. B-tree insert가 끝쪽으로 모여 UUID v4의 page split 문제를 완화한다. RFC 9562 표준이다. | Long보다 16 byte라 인덱스 크기는 크다. 이 비용은 분산 생성, 외부 노출 안정성, 시간 정렬 특성을 위해 수용한다. |
| ULID | 시간 정렬이 가능하고 Crockford Base32 표현이 사람이 읽기 쉽다. | UUID v7이 표준화된 이후 같은 목적에서는 UUID v7 쪽이 JVM, DB, 라이브러리 생태계와 맞추기 쉽다. 문자열 저장 시 byte 효율도 떨어진다. |
| Snowflake | 8 byte Long 기반으로 작고, 시간 정렬과 분산 발급이 가능하다. | machine id 할당, clock drift 대응, sequence 관리가 필요하다. 현재 프로젝트 규모에서는 운영 복잡도가 크다. |

UUID v7을 선택한 이유:

- 애플리케이션에서 ID를 생성할 수 있어 DB round-trip 없이 신규 객체를 만들 수 있다.
- 시간 정렬 특성이 있어 B-tree 인덱스에서 insert locality가 좋아진다.
- 랜덤 UUID v4 대비 primary key insert 시 page split 가능성을 줄일 수 있다.
- 분산 환경에서도 중앙 ID 발급기에 강하게 의존하지 않는다.

### 도메인 prefix / 합성 ID 미채택

도메인 정보를 ID에 섞는 방식도 고려할 수 있지만, primary key 자체에는 적용하지 않는다.

| 방식 | 미채택 이유 |
| --- | --- |
| 표시 prefix (`usr_...`, `kwd_...`) | DB primary key를 문자열로 저장하면 key 길이가 늘어 B-tree fan-out이 줄고, 인덱스 비교 비용이 커진다. prefix는 application 표시용으로만 둘 수 있고, 저장용 PK는 16 byte UUID로 유지한다. |
| UUID 비트에 도메인 토큰 삽입 | UUID v7의 timestamp/random bit 구성을 훼손한다. 시간 정렬과 충돌 회피 속성이 약해지고, 표준 UUID로 보기 어려워진다. |
| 합성 ID (`tenant_channel_ts_seq`, `yymmdd_seq`) | 도메인 속성이 ID에 들어가면 해당 속성 변경이 ID 변경 문제로 이어진다. PK는 도메인 속성과 직교해야 한다. 또한 자리수, sequence, 충돌 범위 같은 운영 부담이 생긴다. |

도메인 정보는 ID에 섞지 않고 별도 컬럼으로 둔다.

예:

```text
user_id
keyword_id
channel
provider
registered_at
```

ID value object를 선택한 이유:

- 서로 다른 aggregate ID를 타입으로 구분한다.
- `UserId`와 `KeywordId`가 모두 UUID 기반이어도 잘못된 전달을 컴파일 단계에서 막는다.
- ID 생성 정책을 `newId()`에 모아 나중에 전략 변경이 쉽다.
- 도메인 코드에서 식별자의 의미가 명확해진다.

## 결과

- persistence adapter와 web adapter에서는 `id.value`를 꺼내거나 `UserId.of(uuid)`처럼 변환하는 매핑 코드가 필요하다.
- 단순히 `UUID`를 쓰는 것보다 파일과 타입이 늘어난다.
- 대신 도메인 계층에서는 식별자 의미가 명확해지고, 잘못된 ID 전달 가능성이 줄어든다.
- UUID 생성 방식 변경이 필요하면 각 ID value object의 `newId()`만 수정하면 된다.
