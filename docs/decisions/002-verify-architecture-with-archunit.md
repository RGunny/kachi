# 002. Verify Architecture Rules With ArchUnit

## Status

Accepted

## Context

Kachi는 서비스별로 Hexagonal Architecture를 따른다. 하지만 패키지 구조만 정해두면 시간이 지나면서 `application`이 `adapter`에 의존하거나, `domain`에 Spring/JPA 의존성이 들어갈 수 있다.

아키텍처 규칙은 문서만으로 유지하기 어렵기 때문에 테스트 단계에서 자동으로 확인할 필요가 있다.

## Decision

각 서비스는 필요한 수준의 ArchUnit 테스트를 둔다.

우선 다음 규칙부터 검증한다.

- `domain`은 외부 계층에 의존하지 않는다.
- `application`은 `adapter`에 의존하지 않는다.
- persistence 구현체와 Spring Data repository는 `adapter.out.persistence`에 둔다.
- HTTP controller, messaging listener, scheduler는 `adapter.in`에 둔다.

## Consequences

- 아키텍처 의존 위반을 테스트 단계에서 발견할 수 있다.
- 헥사고날 구조가 코드 리뷰자의 기억에만 의존하지 않게 된다.
- 초기 개발 단계에서는 규칙이 과도하게 엄격해지지 않도록 핵심 의존 규칙부터 적용한다.
