package me.rgunny.kachi.ai.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("LlmCircuitBreakerProperties")
class LlmCircuitBreakerPropertiesTest {

    @Test
    @DisplayName("정상 범위 값으로 생성된다")
    fun createWithValidValues() {
        val properties = properties()

        assertEquals(6, properties.slidingWindowSize)
        assertEquals(3, properties.minimumNumberOfCalls)
        assertEquals(50f, properties.failureRateThreshold)
        assertEquals(80f, properties.slowCallRateThreshold)
        assertEquals(Duration.ofSeconds(60), properties.waitDurationInOpenState)
        assertEquals(2, properties.permittedNumberOfCallsInHalfOpenState)
    }

    @Test
    @DisplayName("실패율 임계치는 0 초과 100 이하여야 한다")
    fun rejectFailureRateThresholdOutOfRange() {
        assertFailsWith<IllegalArgumentException> { properties(failureRateThreshold = 0f) }
        assertFailsWith<IllegalArgumentException> { properties(failureRateThreshold = 100.1f) }

        assertEquals(100f, properties(failureRateThreshold = 100f).failureRateThreshold)
    }

    @Test
    @DisplayName("느린 호출 비율 임계치는 0 초과 100 이하여야 한다")
    fun rejectSlowCallRateThresholdOutOfRange() {
        assertFailsWith<IllegalArgumentException> { properties(slowCallRateThreshold = 0f) }
        assertFailsWith<IllegalArgumentException> { properties(slowCallRateThreshold = 100.1f) }

        assertEquals(100f, properties(slowCallRateThreshold = 100f).slowCallRateThreshold)
    }

    @Test
    @DisplayName("sliding window 크기는 1 이상이어야 한다")
    fun rejectNonPositiveSlidingWindowSize() {
        assertFailsWith<IllegalArgumentException> {
            properties(slidingWindowSize = 0, minimumNumberOfCalls = 0)
        }
    }

    @Test
    @DisplayName("최소 호출 수는 1 이상 sliding window 크기 이하여야 한다")
    fun rejectMinimumNumberOfCallsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { properties(minimumNumberOfCalls = 0) }
        assertFailsWith<IllegalArgumentException> { properties(minimumNumberOfCalls = 7) }

        assertEquals(6, properties(minimumNumberOfCalls = 6).minimumNumberOfCalls)
    }

    @Test
    @DisplayName("half-open 허용 호출 수는 1 이상이어야 한다")
    fun rejectNonPositivePermittedCalls() {
        assertFailsWith<IllegalArgumentException> {
            properties(permittedNumberOfCallsInHalfOpenState = 0)
        }
    }

    @Test
    @DisplayName("open 상태 대기 시간은 양수여야 한다")
    fun rejectNonPositiveWaitDuration() {
        assertFailsWith<IllegalArgumentException> { properties(waitDurationInOpenState = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> {
            properties(waitDurationInOpenState = Duration.ofSeconds(-1))
        }
    }

    private fun properties(
        slidingWindowSize: Int = 6,
        minimumNumberOfCalls: Int = 3,
        failureRateThreshold: Float = 50f,
        slowCallRateThreshold: Float = 80f,
        waitDurationInOpenState: Duration = Duration.ofSeconds(60),
        permittedNumberOfCallsInHalfOpenState: Int = 2
    ): LlmCircuitBreakerProperties {
        return LlmCircuitBreakerProperties(
            slidingWindowSize = slidingWindowSize,
            minimumNumberOfCalls = minimumNumberOfCalls,
            failureRateThreshold = failureRateThreshold,
            slowCallRateThreshold = slowCallRateThreshold,
            waitDurationInOpenState = waitDurationInOpenState,
            permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState
        )
    }
}
