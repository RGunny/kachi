# 017. MongoDB replica set 전환과 트랜잭션 전제

## 배경

notification 은 MongoDB에 여러 document를 하나의 트랜잭션으로 저장해야 한다.

예:

```text
RequestNotificationUseCase
  -> Notification 저장
  -> NotificationOutbox 저장
```

```text
PublishNotificationDispatchUseCase
  -> Outbox publish 결과 저장
  -> Notification publish 상태 저장
```

MongoDB standalone은 단일 document write 원자성은 제공하지만, 여러 document 또는 여러 collection을 하나의 transaction으로 묶는 multi-document transaction을 지원하지 않는다.
MongoDB multi-document transaction은 replica set 또는 sharded cluster에서만 사용할 수 있다.

현재 로컬 compose의 MongoDB는 standalone으로 기동되고 있었으므로, transaction boundary를 실제로 검증할 수 없는 상태였다.

## 결정

로컬 MongoDB를 단일 노드 replica set으로 전환한다.

```text
mongod --replSet rs0 --bind_ip_all
```

설정 의미:

| 설정 | 의미 | 이유 |
| --- | --- | --- |
| `mongod` | MongoDB server process 실행 | Docker container의 main process |
| `--replSet rs0` | 이 MongoDB 인스턴스를 `rs0` replica set 멤버로 실행 | multi-document transaction 사용 전제 충족 |
| `--bind_ip_all` | 모든 network interface에서 접속 허용 | host에서 실행하는 로컬 애플리케이션과 Docker network 접근을 모두 허용 |

`--replSet`은 mongod를 replica set 모드로 띄우는 설정일 뿐이고, 실제 replica set 구성은 별도 초기화가 필요하다.
따라서 compose healthcheck에서 다음 순서로 초기화한다.

```text
rs.status()
  -> 이미 초기화되어 있으면 ok
  -> 아직 초기화되지 않았으면 rs.initiate(...)
```

초기화 설정:

```javascript
{
  _id: "rs0",
  members: [
    { _id: 0, host: "localhost:27017" }
  ]
}
```

로컬 compose는 인프라 container만 띄우고 애플리케이션은 host에서 실행하는 개발 방식을 기준으로 한다.
따라서 replica set member host를 `localhost:27017`로 둔다.
나중에 애플리케이션도 container 내부에서 실행하는 구성이 되면, replica set member hostname과 connection URI 전략을 다시 잡아야 한다.

애플리케이션 local profile의 MongoDB URI에는 replica set 이름을 명시한다.

```text
mongodb://localhost:27017/{database}?replicaSet=rs0
```

`replicaSet=rs0`는 client가 replica set topology를 기준으로 접속하도록 하는 옵션이다.
transaction을 사용하는 서비스는 standalone URI가 아니라 replica set URI로 접속해야 한다.

## 트랜잭션 경계 원칙

MongoDB transaction은 MongoDB write만 rollback할 수 있다.
Kafka publish, Redis SETNX, 외부 vendor HTTP 호출은 MongoDB transaction에 포함되지 않는다.

따라서 notification use case는 다음 원칙을 따른다.

```text
DB끼리 함께 확정되어야 하는 상태 변경
  -> MongoDB transaction으로 묶는다.

Kafka / Redis / 외부 HTTP side effect
  -> MongoDB transaction 밖에서 실행한다.
  -> claim/finalize 상태 전이, outbox retry, dedupe, idempotency key로 보정한다.
```

예:

```text
request()
  -> Redis request dedupe acquire            (Mongo transaction 밖)
  -> Notification + NotificationOutbox save  (Mongo transaction)
  -> 실패 시 Redis dedupe release
```

```text
publishPending()
  -> Outbox PENDING -> PUBLISHING claim  (짧은 Mongo 저장 경계)
  -> Kafka publish                       (Mongo transaction 밖)
  -> Outbox + Notification 결과 반영      (Mongo transaction)
```

```text
dispatch()
  -> Redis dispatch dedupe acquire           (Mongo transaction 밖)
  -> Notification PROCESSING claim           (짧은 Mongo 저장 경계)
  -> Redis vendor idempotency key 조회/생성   (Mongo transaction 밖)
  -> vendor HTTP send                        (Mongo transaction 밖)
  -> Notification 최종 상태 조건부 반영        (PROCESSING claim CAS)
```

이 구조는 하나의 상위 use case 메서드가 전체 흐름을 조율하되, DB transaction이 필요한 구간과 외부 side effect 구간을 명시적으로 분리한다.
외부 side effect 이후의 dispatch finalize는 `_id + status + claimedAt + claimedBy` 조건으로만 저장한다.
CAS 조건 불일치는 같은 조건으로 재시도해도 성공하지 않는 소유권 상실 신호이므로, 저장소 장애 retry와 구분한다.

## Sharding

sharding은 이번 범위에 포함하지 않는다.

Replica set은 transaction, failover, 복제, 운영 안정성을 위한 기본 전제다.
Sharding은 단일 replica set으로 데이터 크기나 쓰기/읽기 부하를 감당하기 어려워졌을 때 도입하는 수평 분산 전략이다.

후속 TODO:

- MongoDB sharding이 필요한 데이터 크기/트래픽 기준 정의
- shard key 후보 검토
- notification/collector/ai collection별 shard key 적합성 검토
- sharded cluster의 transaction 비용과 운영 복잡도 검토

## 트레이드오프

## 운영 환경과의 차이

이번 compose 설정은 로컬 개발용 단일 노드 replica set이다.
운영에서 replica set은 다음처럼 구성한다.

```text
primary
secondary
secondary
```
현재 설정은 운영 topology를 완전히 재현하기 위한 것이 아니라, 로컬에서도 MongoDB transaction 전제를 만족시키기 위한 최소 구성이다.

### 장점

- 로컬과 테스트에서 MongoDB transaction 전제를 검증할 수 있다.
- notification의 `Notification + Outbox` 같은 다중 document 저장 경계를 안전하게 구현할 수 있다.
- 운영 MongoDB 구조에 가까운 개발 환경을 갖는다.

### 단점

- 로컬 MongoDB 초기화가 standalone보다 복잡하다.
- 기존 standalone volume을 사용하던 환경에서는 replica set 초기화 상태를 확인해야 한다.
- 컨테이너 내부 애플리케이션이 MongoDB에 접속하는 배포 구성이 생기면 replica set member hostname 전략을 다시 잡아야 한다.

## 후속 작업

1. `RequestNotificationUseCase`의 `Notification + Outbox` 저장을 하나의 persistence port와 Mongo transaction으로 묶는다.
2. `PublishNotificationDispatchUseCase`의 claim / Kafka publish / finalize 경계를 명시적으로 분리한다.
3. `DispatchNotificationUseCase`의 claim / vendor send / finalize 경계를 명시적으로 분리한다.
4. Testcontainers MongoDB도 replica set URI 기준으로 동작하는지 검증한다.
