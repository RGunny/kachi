package me.rgunny.kachi.ai.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * LLM provider 호출에 적용할 circuit breaker 설정과 registry를 구성한다.
 *
 * 설정 하나를 provider 전체가 공유하고 집계 window만 provider별로 갈린다.
 * registry를 쓰는 이유는 이름으로 인스턴스를 꺼내 쓸 수 있고, metric 수집을 붙일 때의 진입점이기 때문이다.
 */
@Configuration
class LlmCircuitBreakerConfig {

    @Bean
    fun llmCircuitBreakerRegistry(properties: LlmProviderProperties): CircuitBreakerRegistry {
        return CircuitBreakerRegistry.of(circuitBreakerConfig(properties.circuitBreaker))
    }

    /**
     * 재시도로 풀릴 수 있는 LLM 실패만 회로를 여는 근거로 삼는다.
     *
     * 응답 계약 위반이나 인증 실패는 provider가 살아 있다는 증거이므로 기록하지 않는다.
     * 집계 기준은 시간이 아니라 호출 횟수다. 호출량이 적어 시간 window는 비율이 흔들린다.
     */
    fun circuitBreakerConfig(properties: LlmCircuitBreakerProperties): CircuitBreakerConfig {
        return CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(properties.slidingWindowSize)
            .minimumNumberOfCalls(properties.minimumNumberOfCalls)
            .failureRateThreshold(properties.failureRateThreshold)
            .slowCallDurationThreshold(properties.slowCallDurationThreshold)
            .slowCallRateThreshold(properties.slowCallRateThreshold)
            .waitDurationInOpenState(properties.waitDurationInOpenState)
            .permittedNumberOfCallsInHalfOpenState(properties.permittedNumberOfCallsInHalfOpenState)
            .recordException { it is LlmProviderException && it.failure.retryable }
            .build()
    }
}
