# 005. 로컬 인프라 Docker Compose 구성

## 배경

`user-service`의 refresh token 저장소로 Redis를 사용한다.
애플리케이션 설정은 `REDIS_HOST`, `REDIS_PORT`를 통해 Redis에 접속하도록 준비되어 있지만, 프로젝트 안에 Redis 실행 방법이 없으면 로컬 개발 환경을 재현하기 어렵다.

Kachi는 여러 인프라 컴포넌트를 사용할 수 있지만, 로컬 개발 인프라는 구현된 기능이 실제로 필요로 하는 범위만 제공한다.
현재 `user-service`의 refresh token 저장소는 Redis만 필요로 한다.

## 결정

프로젝트에는 Redis 실행에 필요한 최소 Docker Compose를 둔다.

```text
infra/docker-compose.yml
infra/docker-compose.redis.yml
```

실행 명령:

```sh
docker compose -f infra/docker-compose.yml -f infra/docker-compose.redis.yml up -d
```

`docker-compose.yml`에는 프로젝트 공통 요소를 둔다.

- Compose project name
- 공통 network
- Redis volume

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

## 이유

### Redis만 우선 추가

현재 코드에서 실제로 필요한 외부 인프라는 refresh token 저장소인 Redis다.
MySQL, MongoDB, Kafka까지 한 번에 추가하면 아직 사용하지 않는 실행 경로와 설정 관리 부담이 생긴다.

따라서 현재 필요한 Redis만 추가하고, MySQL/MongoDB/Kafka는 해당 서비스나 persistence 구현이 실제로 붙는 시점에 추가한다.

### 공통 compose와 서비스 compose 분리

공통 compose와 서비스별 compose를 분리하면 필요한 인프라만 조합해서 실행할 수 있다.

예:

```sh
docker compose -f infra/docker-compose.yml -f infra/docker-compose.redis.yml up -d
```

추후 MySQL이 필요해지면 `docker-compose.mysql.yml`만 추가해서 같은 공통 network와 volume 정책을 재사용할 수 있다.

### `redis:7-alpine`

Redis 7은 현재 refresh token 저장소 요구사항인 TTL, key 존재 확인, Lua script 실행을 충분히 지원한다.
`alpine` 이미지는 로컬 개발용으로 가볍고 시작이 빠르다.

### `REDIS_PORT:-6379`

기본값은 Redis 표준 포트인 `6379`로 둔다.
다만 로컬 머신에 이미 Redis가 떠 있거나 다른 프로젝트가 같은 포트를 사용하면 `REDIS_PORT` 환경변수로 변경할 수 있게 한다.

### Volume 사용

Redis 데이터는 컨테이너 재시작 후에도 유지되도록 named volume에 저장한다.
refresh token은 TTL이 있는 데이터라 영구 보존 대상은 아니지만, 로컬에서 컨테이너 재시작만으로 모든 로그인 상태가 사라지는 것을 피할 수 있다.

Compose 파일 안의 volume key는 `redis-data`로 둔다.
이름은 인프라 컴포넌트와 리소스 성격을 함께 드러내는 `{component}-{resource}` 형식을 따른다.

실제 Docker volume 이름은 Compose project name을 통해 `kachi_redis-data`로 생성된다.
프로젝트 prefix는 Compose에 맡기고, 서비스별 compose 파일에서는 논리 이름만 관리한다.

따라서 로컬 인프라 리소스 이름은 다음 기준을 따른다.

```text
{component}-{resource}
```

예:

```text
redis-data
app-network
```

### AOF 활성화

`redis-server --appendonly yes`로 AOF를 활성화한다.
로컬 개발에서도 Redis 재시작 시 refresh token key가 유지되는 편이 인증 흐름 테스트에 편하다.

다만 refresh token은 TTL을 가진 인증 보조 데이터이므로, 운영 환경에서는 장애 복구 정책과 보안 정책에 맞춰 persistence 수준을 다시 검토해야 한다.

### Healthcheck

`redis-cli ping` healthcheck를 둔다.
로컬에서 Redis 컨테이너가 떠 있는지 Docker 상태만으로 빠르게 확인할 수 있고, 나중에 다른 서비스가 Redis 의존성을 갖게 될 때 readiness 판단 근거로 확장할 수 있다.

### Resource limit 미적용

현재 compose는 로컬 개발용 최소 구성이라 resource limit은 두지 않는다.

Docker Compose의 `deploy.resources`는 일반 `docker compose up` 로컬 실행에서 기대한 방식으로 강제되지 않는 경우가 있고, 현재 단계에서는 설정 복잡도 대비 이득이 작다.
운영 배포나 CI 리소스 제한이 필요해지는 시점에 별도 프로파일 또는 배포 설정에서 다룬다.

## 결과

- Redis가 필요한 기능을 로컬에서 재현할 수 있다.
- 현재 구현 범위에 필요한 인프라만 실행한다.
- 인프라가 늘어날 때 공통 compose에 network/volume을 추가하고, 서비스별 compose를 조합하는 방식으로 확장한다.
- Docker Compose는 로컬 개발 편의용이며 운영 배포 설정과 동일하다고 보지 않는다.
