# 005. 로컬 인프라 Docker Compose 구성

## 배경

`user-service`는 사용자와 키워드를 MySQL에 저장하고, refresh token 상태를 Redis에 저장한다.
`collector-service`는 수집 뉴스와 수집 실행 기록을 MongoDB에 저장한다.
`notification-service`와 `notification-worker`는 Redis, MongoDB, Kafka를 사용한다.
애플리케이션 설정은 `MYSQL_*`, `REDIS_*`, `MONGO_*` 환경변수를 통해 인프라에 접속하도록 준비되어 있지만, 프로젝트 안에 실행 방법이 없으면 로컬 개발 환경을 재현하기 어렵다.

Kachi는 여러 인프라 컴포넌트를 사용할 수 있지만, 로컬 개발 인프라는 구현된 기능이 실제로 필요로 하는 범위만 제공한다.
notification 모듈이 Kafka 기반 dispatch/outbox/worker 흐름으로 확장되면서 로컬 개발 인프라에는 Kafka도 포함한다.

## 결정

프로젝트에는 user-service 실행에 필요한 MySQL/Redis, collector-service와 notification 실행에 필요한 MongoDB, notification dispatch 흐름에 필요한 Kafka Docker Compose를 둔다.

```text
infra/docker-compose.yml
infra/docker-compose.mysql.yml
infra/docker-compose.redis.yml
infra/docker-compose.mongo.yml
infra/docker-compose.kafka.yml
```

실행 명령:

```sh
docker compose -f infra/docker-compose.yml -f infra/docker-compose.mysql.yml -f infra/docker-compose.redis.yml -f infra/docker-compose.mongo.yml -f infra/docker-compose.kafka.yml up -d
```

`docker-compose.yml`에는 프로젝트 공통 요소를 둔다.

- Compose project name
- 공통 network
- MySQL volume
- Redis volume
- MongoDB volume
- Kafka volume

`docker-compose.mysql.yml`에는 MySQL 서비스만 둔다.

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: kachi-mysql
    ports:
      - "${MYSQL_PORT:-3306}:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - app-network
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
```

`docker-compose.redis.yml`에는 Redis 서비스만 둔다.

```yaml
services:
  redis:
    image: redis:7-alpine
    container_name: kachi-redis
    ports:
      - "${REDIS_PORT:-6379}:6379"
    volumes:
      - redis-data:/data
    networks:
      - app-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
    command: redis-server --appendonly yes
```

`docker-compose.mongo.yml`에는 MongoDB 서비스만 둔다.
notification의 multi-document transaction 검증을 위해 MongoDB는 standalone이 아니라 단일 노드 replica set으로 실행한다.

```yaml
services:
  mongo:
    image: mongo:7.0
    container_name: kachi-mongo
    command: ["mongod", "--replSet", "${MONGO_REPLICA_SET:-rs0}", "--bind_ip_all"]
    environment:
      MONGO_INITDB_DATABASE: ${MONGO_DATABASE:-kachi_collector}
      TZ: Asia/Seoul
    ports:
      - "${MONGO_PORT:-27017}:27017"
    volumes:
      - mongo-data:/data/db
    networks:
      - app-network
    healthcheck:
      test:
        [
          "CMD",
          "mongosh",
          "--quiet",
          "--eval",
          "try { rs.status().ok } catch (e) { rs.initiate({_id: '${MONGO_REPLICA_SET:-rs0}', members: [{ _id: 0, host: 'localhost:27017' }] }).ok }"
        ]
```

MongoDB command 의미와 이유:

| 설정 | 의미 | 이유 |
| --- | --- | --- |
| `mongod` | MongoDB server process 실행 | Docker container의 main process로 MongoDB server를 직접 실행한다. |
| `--replSet ${MONGO_REPLICA_SET:-rs0}` | replica set 이름을 지정해 mongod를 replica set 모드로 실행 | MongoDB multi-document transaction은 standalone에서 동작하지 않으므로 로컬도 replica set topology로 맞춘다. |
| `--bind_ip_all` | 모든 network interface에서 접속 허용 | host에서 실행하는 애플리케이션과 Docker network 접근을 모두 허용한다. |

`--replSet`만으로 replica set이 바로 초기화되지는 않는다.
healthcheck는 `rs.status()`로 초기화 여부를 확인하고, 아직 초기화되지 않았으면 단일 멤버 replica set을 `rs.initiate(...)`로 만든다.
현재 로컬 개발은 애플리케이션을 host에서 실행하는 방식을 기준으로 하므로 replica set member host는 `localhost:27017`로 둔다.
운영 환경에서는 이 command를 그대로 쓰기보다 MongoDB config file, Atlas, Kubernetes Operator, Helm chart, VM/systemd, IaC 등으로 replica set을 구성한다.
현재 설정은 운영 topology 전체를 재현하기 위한 것이 아니라 로컬에서도 transaction 전제를 검증하기 위한 최소 구성이다.

`docker-compose.kafka.yml`에는 Kafka 서비스만 둔다.

```yaml
services:
  kafka:
    image: apache/kafka:3.9.1
    container_name: kachi-kafka
    ports:
      - "${KAFKA_PORT:-9092}:9092"
    volumes:
      - kafka-data:/var/lib/kafka/data
    networks:
      - app-network
```

## 이유

### 구현 단계에 맞춰 필요한 인프라만 추가

초기에는 사용자/키워드 저장소인 MySQL, refresh token 저장소인 Redis, 뉴스/수집 실행 저장소인 MongoDB만 필요했다.
notification 모듈은 요청 접수 후 outbox를 통해 `notification.dispatch`를 발행하고 worker가 이를 consume하는 구조이므로 Kafka가 로컬 필수 인프라가 된다.

따라서 Kafka는 notification-service/worker adapter를 구현하는 시점에 `docker-compose.kafka.yml`로 분리 추가한다.
Kafka가 필요 없는 user/collector 개발에서는 해당 compose 파일을 조합하지 않으면 된다.

### 공통 compose와 서비스 compose 분리

공통 compose와 서비스별 compose를 분리하면 필요한 인프라만 조합해서 실행할 수 있다.

예:

```sh
docker compose -f infra/docker-compose.yml -f infra/docker-compose.mysql.yml -f infra/docker-compose.redis.yml -f infra/docker-compose.mongo.yml -f infra/docker-compose.kafka.yml up -d
```

MySQL, Redis, MongoDB, Kafka를 함께 실행할 때는 공통 compose에 서비스별 compose를 조합한다.
다른 인프라가 필요해지면 같은 방식으로 `docker-compose.{component}.yml`을 추가한다.

### `mysql:8.0`

MySQL은 user-service의 JPA 저장소 실행을 위한 로컬 관계형 DB로 사용한다.

MySQL 8.0은 현재 사용하는 `com.mysql:mysql-connector-j`와 호환되고, UUID 문자열 컬럼과 일반 B-tree 인덱스 사용에 충분하다.
버전 차이로 인한 SQL dialect, collation, 인증 방식 차이를 줄이기 위해 로컬 기본 이미지는 명시적으로 `mysql:8.0`으로 고정한다.

### `MYSQL_*` 기본값

로컬 compose에는 다음 기본값을 둔다.

| 환경변수 | 기본값 | 이유 |
| --- | --- | --- |
| `MYSQL_DATABASE` | `kachi` | user-service 기본 데이터베이스 |
| `MYSQL_USER` | `rgunny` | 로컬 개발 계정 |
| `MYSQL_PASSWORD` | `rgunny` | 로컬 개발용 비밀번호 |
| `MYSQL_ROOT_PASSWORD` | `root` | 컨테이너 초기화용 root 비밀번호 |
| `MYSQL_PORT` | `3306` | MySQL 표준 포트 |

이 값은 로컬 개발 편의용이며 운영 secret으로 사용하지 않는다.
운영 공통 설정은 환경변수 주입을 요구하고 기본 비밀번호를 제공하지 않는다.

### 설정 파일 분리

`application.yaml`에는 환경변수 기반 필수 설정 구조를 둔다.
운영 또는 CI에서 값이 누락되면 애플리케이션 기동 시점에 바로 드러나게 하기 위함이다.

`application-local.yaml`에는 로컬 개발 기본값을 둔다.
MySQL, Redis, MongoDB, Kafka, OAuth2 callback처럼 로컬에서 반복 실행해야 하는 값은 기본값을 제공해 별도 `.env` 없이도 빠르게 실행할 수 있게 한다.

따라서 `REDIS_HOST`, `REDIS_PORT`, `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MONGO_HOST`, `MONGO_PORT`, `MONGO_DATABASE`, `KAFKA_BOOTSTRAP_SERVERS`의 로컬 기본값은 `application-local.yaml`에만 둔다.

### `ddl-auto`

로컬 프로필도 `spring.jpa.hibernate.ddl-auto=validate`를 사용한다.
초기에는 손타이핑하며 빠르게 확인하기 위해 local에서 `update`를 사용했지만, MySQL 인프라와 JPA adapter 통합 테스트를 붙인 뒤부터는 schema를 migration 파일로 명시한다.

기본 설정과 로컬 설정을 모두 `validate`로 둔다.
Hibernate가 임의로 schema를 변경하지 않고, entity와 migration schema가 어긋나면 애플리케이션 기동 시점에 드러나게 하기 위함이다.

schema 생성과 변경은 Flyway migration 파일로 관리한다.

### UTF-8 설정

MySQL server character set은 `utf8mb4`, collation은 `utf8mb4_unicode_ci`로 둔다.
키워드와 닉네임에는 한글, 영문, 숫자뿐 아니라 이모지나 다국어 문자가 들어올 수 있으므로 4 byte Unicode까지 저장 가능한 설정을 기본값으로 둔다.

### init.sql 미사용

현재는 `MYSQL_DATABASE` 하나만 필요하다.
MySQL 공식 이미지가 `MYSQL_DATABASE` 값을 기준으로 초기 데이터베이스를 생성하므로 별도 `init.sql`을 두지 않는다.

추가 서비스용 데이터베이스나 초기 권한 부여가 필요해지는 시점에 `infra/config/mysql/init.sql`을 도입한다.

### `redis:7-alpine`

Redis 7은 현재 refresh token 저장소 요구사항인 TTL, key 존재 확인, Lua script 실행을 충분히 지원한다.
`alpine` 이미지는 로컬 개발용으로 가볍고 시작이 빠르다.

### `REDIS_PORT:-6379`

기본값은 Redis 표준 포트인 `6379`로 둔다.
다만 로컬 머신에 이미 Redis가 떠 있거나 다른 프로젝트가 같은 포트를 사용하면 `REDIS_PORT` 환경변수로 변경할 수 있게 한다.

### `mongo:7.0`

MongoDB는 collector-service의 뉴스와 수집 실행 기록 저장소로 사용한다.

뉴스 원문 URL hash, provider별 수집 결과, 매칭 키워드 목록처럼 문서 단위로 함께 저장하고 조회하는 데이터가 많기 때문에 초기 collector 저장소는 MongoDB로 둔다.

로컬 기본 이미지는 `mongo:7.0`으로 고정한다.
Spring Data MongoDB reactive adapter와 Testcontainers MongoDB 테스트 기준에 맞춰 UUID representation은 `standard`로 사용한다.

MongoDB multi-document transaction은 replica set 또는 sharded cluster에서만 동작한다.
notification의 `Notification + NotificationOutbox` 같은 저장 경계를 실제로 검증하기 위해 로컬 MongoDB도 단일 노드 replica set으로 실행한다.
sharding은 현재 데이터 규모와 운영 복잡도를 고려해 도입하지 않고, 별도 후속 작업으로 검토한다.

### `MONGO_*` 기본값

로컬 compose에는 다음 기본값을 둔다.

| 환경변수 | 기본값 | 이유 |
| --- | --- | --- |
| `MONGO_HOST` | `localhost` | 로컬 collector-service 접속 host |
| `MONGO_PORT` | `27017` | MongoDB 표준 포트 |
| `MONGO_DATABASE` | `kachi_collector` | collector-service 기본 데이터베이스 |
| `MONGO_REPLICA_SET` | `rs0` | 로컬 MongoDB 단일 노드 replica set 이름 |

### `apache/kafka:3.9.1`

Kafka는 notification-service가 `notification.dispatch`를 발행하고 notification-worker가 consume하는 로컬 메시지 broker로 사용한다.

로컬 개발에서는 Zookeeper 없는 KRaft 단일 broker 구성을 사용한다.
운영 다중 broker 복제 구성을 재현하기 위한 목적이 아니라, producer/consumer/retry topic 계약을 로컬에서 확인하기 위한 최소 구성이다.

### `KAFKA_*` 기본값

로컬 compose에는 다음 기본값을 둔다.

| 환경변수 | 기본값 | 이유 |
| --- | --- | --- |
| `KAFKA_HOST` | `localhost` | 로컬 Kafka 접속 host |
| `KAFKA_PORT` | `9092` | Kafka 표준 client port |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Spring Kafka bootstrap servers |

### Volume 사용

Redis 데이터는 컨테이너 재시작 후에도 유지되도록 named volume에 저장한다.
refresh token은 TTL이 있는 데이터라 영구 보존 대상은 아니지만, 로컬에서 컨테이너 재시작만으로 모든 로그인 상태가 사라지는 것을 피할 수 있다.

MySQL 데이터도 컨테이너 재시작 후 유지되도록 named volume에 저장한다.
사용자와 키워드는 로컬 개발 중 반복 확인해야 하는 영속 데이터이므로 컨테이너 재시작만으로 사라지지 않는 편이 좋다.

MongoDB 데이터도 컨테이너 재시작 후 유지되도록 named volume에 저장한다.

Kafka 데이터도 컨테이너 재시작 후 유지되도록 named volume에 저장한다.
로컬 topic과 consumer offset을 유지하면 service/worker 재기동 시 retry, DLT, offset 처리 흐름을 반복 확인하기 쉽다.

Compose 파일 안의 volume key는 `mysql-data`, `redis-data`, `mongo-data`, `kafka-data`로 둔다.
이름은 인프라 컴포넌트와 리소스 성격을 함께 드러내는 `{component}-{resource}` 형식을 따른다.

실제 Docker volume 이름은 Compose project name을 통해 `kachi_mysql-data`, `kachi_redis-data`, `kachi_mongo-data`, `kachi_kafka-data`로 생성된다.
프로젝트 prefix는 Compose에 맡기고, 서비스별 compose 파일에서는 논리 이름만 관리한다.

따라서 로컬 인프라 리소스 이름은 다음 기준을 따른다.

```text
{component}-{resource}
```

예:

```text
redis-data
mysql-data
mongo-data
kafka-data
app-network
```

### AOF 활성화

`redis-server --appendonly yes`로 AOF를 활성화한다.
로컬 개발에서도 Redis 재시작 시 refresh token key가 유지되는 편이 인증 흐름 테스트에 편하다.

다만 refresh token은 TTL을 가진 인증 보조 데이터이므로, 운영 환경에서는 장애 복구 정책과 보안 정책에 맞춰 persistence 수준을 다시 검토해야 한다.

### Healthcheck

MySQL은 `mysqladmin ping`, Redis는 `redis-cli ping`, MongoDB는 `mongosh` 기반 replica set status/init healthcheck를 둔다.
로컬에서 컨테이너가 떠 있는지 Docker 상태만으로 빠르게 확인할 수 있고, 나중에 의존 서비스가 늘어날 때 readiness 판단 근거로 확장할 수 있다.

### Resource limit 미적용

현재 compose는 로컬 개발용 최소 구성이라 resource limit은 두지 않는다.

Docker Compose의 `deploy.resources`는 일반 `docker compose up` 로컬 실행에서 기대한 방식으로 강제되지 않는 경우가 있고, 현재 단계에서는 설정 복잡도 대비 이득이 작다.
운영 배포나 CI 리소스 제한이 필요해지는 시점에 별도 프로파일 또는 배포 설정에서 다룬다.

## 결과

- MySQL이 필요한 user-service JPA 저장소를 로컬에서 재현할 수 있다.
- Redis가 필요한 user-service refresh token 저장소를 로컬에서 재현할 수 있다.
- MongoDB가 필요한 collector-service 저장소를 로컬에서 재현할 수 있다.
- 현재 구현 범위에 필요한 인프라만 실행한다.
- 인프라가 늘어날 때 공통 compose에 network/volume을 추가하고, 서비스별 compose를 조합하는 방식으로 확장한다.
- Docker Compose는 로컬 개발 편의용이며 운영 배포 설정과 동일하다고 보지 않는다.
