# 008. collector-service 뉴스 수집 실행 모델

## 배경

`collector-service`는 사용자 관심 키워드를 기준으로 외부 뉴스 provider를 호출하고, 신규 뉴스를 저장해야 한다.

수집은 다음 두 방식으로 시작될 수 있다.

- scheduler에 의한 주기 실행
- internal API에 의한 수동 실행

두 진입점이 동시에 실행되면 같은 키워드와 provider에 대해 중복 요청이 발생할 수 있다.
저장소의 중복 방어가 있더라도 외부 provider 호출 비용, rate limit, 실행 기록 혼선이 생긴다.

현재 배포 모델은 단일 collector 인스턴스를 가정한다.
kachi 프로젝트 전체 서비스 파이프라인의 완성 이후에 개별 서비스를 고도화한다.

## 결정

뉴스 수집 실행은 `NewsCollectionExecutor`를 통해 시작한다.

`NewsCollectionExecutor`는 JVM 내부 `AtomicBoolean` lock으로 같은 인스턴스 안의 중복 실행을 막는다.

```text
Scheduler
  -> NewsCollectionExecutor
      -> CollectNewsUseCase

Internal API
  -> NewsCollectionExecutor
      -> CollectNewsUseCase
```

수집 중복 요청이 들어오면 다음과 같이 처리한다.

- scheduler 실행은 skip하고 로그를 남긴다.
- internal API 실행은 `409 CONFLICT`를 반환한다.

수동 API 요청 body가 없거나 `keywords`가 비어 있으면 `user-service`의 활성 키워드를 조회한다.
요청에 키워드가 있으면 해당 키워드만 수집한다.

## 이유

### 실행 중복 방지 책임 분리

중복 실행 방지를 `CollectNewsService` 안에 넣지 않는다.

`CollectNewsService`는 수집 유스케이스 자체에 집중하고, scheduler/API 같은 입력 어댑터에서 들어온 요청의 중복 실행 방지는 `adapter.in` 계층의 `NewsCollectionExecutor`가 담당한다.

이 지점에서 헥사고날 아키텍처를 도입한 장점이 드러난다.
수집 유스케이스는 입력 어댑터가 scheduler인지 HTTP API인지, 중복 실행 방지가 JVM 내부 lock인지 distributed lock인지 알 필요가 없다.
application service는 포트와 도메인 규칙에 집중하고, 외부 실행 방식과 인프라 선택은 adapter 계층에서 다룬다.

이는 SOLID 중 SRP와 DIP 관점에 맞다.
- SRP: `CollectNewsService`는 뉴스 수집이라는 단일 책임을 유지하고, 실행 중복 방지 책임을 갖지 않는다.
- DIP: application service는 lock 구현 세부사항에 의존하지 않고, 중복 실행 방지와 인프라 선택은 바깥 adapter 계층에 둔다.

따라서 나중에 lock 구현을 Redis/MongoDB 기반 distributed lock으로 바꿔도 application service의 수집 로직을 바꾸지 않아도 된다.

### 단일 인스턴스 lock

현재는 단일 인스턴스 실행을 가정하므로 `AtomicBoolean` lock으로 충분하다.

이 방식은 단순하고 외부 인프라 의존이 없다.
또한 현재 목적은 scheduler와 수동 API가 같은 JVM 안에서 동시에 같은 수집 작업을 시작하지 못하게 하는 것이다.

단점은 여러 collector 인스턴스를 띄우면 인스턴스마다 lock이 따로 존재한다는 점이다.
따라서 분산 환경에서는 이 결정을 그대로 유지할 수 없다.

### Scheduler 기본값

운영 기본 설정은 scheduler 활성화로 둔다.
하지만 local/test 프로필에서는 자동 외부 호출을 피하기 위해 scheduler를 기본 비활성화한다.

로컬에서 자동 수집을 확인해야 할 때만 `KACHI_COLLECTOR_NEWS_SCHEDULER_ENABLED=true`로 켠다.

## 결과

- scheduler와 internal API가 같은 중복 실행 방지 규칙을 사용한다.
- 단일 인스턴스에서는 불필요한 중복 provider 호출을 줄일 수 있다.
- internal API 호출자는 이미 실행 중인 상태를 `409 CONFLICT`로 알 수 있다.
- 분산 배포가 필요해지는 시점에는 Redis, MongoDB, 또는 별도 coordination 저장소 기반 distributed lock으로 교체해야 한다.

## 제외한 것

현재는 distributed lock을 도입하지 않는다.

아직 여러 collector 인스턴스를 동시에 운영하는 배포 모델이 아니고, Redis/Mongo lock의 TTL, fencing token, 장애 복구 정책까지 결정하기에는 이르기 때문이다.

현재는 `CollectionRun` 조회 API도 제외한다.
수집 실행 결과는 저장하지만, 조회 API는 별도 유스케이스로 분리해 추가한다.
