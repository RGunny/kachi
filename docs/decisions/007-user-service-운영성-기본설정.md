# 007. user-service 운영 기본 설정

## 배경

`user-service`는 인증, 사용자 상태, 관심 키워드 관리 요청을 받는 HTTP API 서비스다.

애플리케이션이 정상 기동했는지 확인할 endpoint와, 종료 시 진행 중인 요청을 최대한 마무리할 기본 설정이 필요하다.

## 결정

`user-service`는 Spring Boot Actuator health endpoint를 노출한다.

```text
/actuator/health
```

노출 범위는 `health`로 제한한다.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
```

헬스체크 endpoint는 인증 없이 접근할 수 있게 한다.
이 endpoint는 사용자 데이터나 내부 설정을 반환하는 API가 아니라 서비스 상태 확인용 API이기 때문이다.

서버 종료 방식은 graceful shutdown으로 둔다.

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

## 근거

### Health endpoint

서비스가 기동했는지 확인하기 위해 별도 비즈니스 API를 호출하면 인증, DB 데이터, 요청 DTO 같은 다른 요소에 영향을 받는다.

`/actuator/health`는 애플리케이션 상태 확인에 목적이 분리되어 있어 앱 실행, Docker 실행, 테스트에서 공통으로 사용하기 쉽다.

### Graceful shutdown

기본 종료는 진행 중인 HTTP 요청이 끊길 수 있다.

Graceful shutdown은 종료 신호를 받은 뒤 새 요청을 더 받지 않고, 이미 처리 중인 요청이 끝날 시간을 준다.

현재 초기값은 30초로 둔다.

- 너무 짧으면 진행 중인 요청을 마무리하기 어렵다.
- 너무 길면 로컬 재시작과 배포 종료가 느려진다.
- `user-service`의 현재 API는 장시간 처리 요청이 없으므로 30초면 충분한 초기값이다.

## 제외한 것

현재는 `/actuator/prometheus`, custom health indicator, readiness/liveness group을 추가하지 않는다.

아직 Prometheus, Kubernetes, 별도 로드밸런서 구성이 없기 때문이다.
해당 인프라가 도입되면 필요한 endpoint와 보안 정책을 별도 결정으로 추가한다.
