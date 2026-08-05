package me.rgunny.kachi.notification.retry

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("RetryPolicy")
class RetryPolicyTest {

    @Test
    @DisplayName("최대 시도 횟수와 지연 시간은 양수여야 한다")
    fun validatePolicyValues() {
        assertFailsWith<IllegalArgumentException> {
            RetryPolicy(maxAttempts = 0, baseDelay = Duration.ofSeconds(1), maxDelay = Duration.ofSeconds(10))
        }
        assertFailsWith<IllegalArgumentException> {
            RetryPolicy(maxAttempts = 3, baseDelay = Duration.ZERO, maxDelay = Duration.ofSeconds(10))
        }
        assertFailsWith<IllegalArgumentException> {
            RetryPolicy(maxAttempts = 3, baseDelay = Duration.ofSeconds(1), maxDelay = Duration.ZERO)
        }
    }

    @Test
    @DisplayName("시도 횟수 기준으로 재시도 한도 도달 여부를 판단한다")
    fun exhaustedByAttempts() {
        val policy = RetryPolicy(
            maxAttempts = 3,
            baseDelay = Duration.ofSeconds(1),
            maxDelay = Duration.ofSeconds(10),
        )

        assertFalse(policy.exhausted(2))
        assertTrue(policy.exhausted(3))
        assertTrue(policy.exhausted(4))
    }

    @Test
    @DisplayName("backoff는 exponential로 증가하고 maxDelay를 넘지 않는다")
    fun calculateBackoff() {
        val policy = RetryPolicy(
            maxAttempts = 5,
            baseDelay = Duration.ofSeconds(2),
            maxDelay = Duration.ofSeconds(10),
        )

        assertEquals(Duration.ofSeconds(2), policy.backoff(1))
        assertEquals(Duration.ofSeconds(4), policy.backoff(2))
        assertEquals(Duration.ofSeconds(8), policy.backoff(3))
        assertEquals(Duration.ofSeconds(10), policy.backoff(4))
    }

    @Test
    @DisplayName("실패 분류와 시도 횟수로 재시도 결정을 내린다")
    fun decideRetry() {
        val policy = RetryPolicy(
            maxAttempts = 3,
            baseDelay = Duration.ofSeconds(1),
            maxDelay = Duration.ofSeconds(10),
        )

        val retryableFailure = RetryFailure.of(RetryFailureCode.VENDOR_TIMEOUT)
        val permanentFailure = retryableFailure.copy(category = FailureCategory.VALIDATION_ERROR)

        assertEquals(RetryDecision.Retry(Duration.ofSeconds(1), retryableFailure), policy.decide(retryableFailure, 1))
        assertEquals(RetryDecision.GiveUp(retryableFailure, exhausted = true), policy.decide(retryableFailure, 3))
        assertEquals(RetryDecision.GiveUp(permanentFailure, exhausted = false), policy.decide(permanentFailure, 1))
    }
}
