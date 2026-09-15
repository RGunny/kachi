package me.rgunny.kachi.story.config

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryOutboxRelayProperties")
class StoryOutboxRelayPropertiesTest {

    @Test
    @DisplayName("발행자 이름은 비어 있을 수 없다")
    fun rejectBlankPublisherId() {
        assertFailsWith<IllegalArgumentException> { StoryTestFixture.relayProperties(publisherId = " ") }
    }

    @Test
    @DisplayName("실행 주기는 양수여야 한다")
    fun rejectNonPositiveFixedDelay() {
        assertFailsWith<IllegalArgumentException> { StoryTestFixture.relayProperties(fixedDelay = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { StoryTestFixture.relayProperties(fixedDelay = Duration.ofSeconds(-1)) }
    }

    @Test
    @DisplayName("최초 실행 지연은 음수일 수 없다")
    fun rejectNegativeInitialDelay() {
        assertFailsWith<IllegalArgumentException> { StoryTestFixture.relayProperties(initialDelay = Duration.ofSeconds(-1)) }

        assertEquals(Duration.ZERO, StoryTestFixture.relayProperties(initialDelay = Duration.ZERO).initialDelay)
    }

    @Test
    @DisplayName("batch 크기는 1 이상이어야 한다")
    fun rejectNonPositiveBatchSize() {
        assertFailsWith<IllegalArgumentException> { StoryTestFixture.relayProperties(batchSize = 0) }
    }

    @Test
    @DisplayName("발행 점유 유효 시간은 양수여야 한다")
    fun rejectNonPositiveVisibilityTimeout() {
        assertFailsWith<IllegalArgumentException> {
            StoryTestFixture.relayProperties(publishingVisibilityTimeout = Duration.ZERO)
        }
    }

    /**
     * 재시도 값의 규칙은 정책이 갖고 있다. 같은 규칙을 설정에도 적어두면 한쪽만 고쳐졌을 때 갈라진다.
     */
    @Test
    @DisplayName("재시도 값 검증은 재시도 정책에 맡긴다")
    fun delegateRetryValidationToPolicy() {
        val properties = StoryTestFixture.relayProperties(retry = StoryTestFixture.retryProperties(maxAttempts = 0))

        assertFailsWith<IllegalArgumentException> { properties.toRetryPolicy() }
    }

    @Test
    @DisplayName("설정값을 relay 실행 정책으로 옮긴다")
    fun mapToRelayPolicy() {
        val policy = StoryTestFixture.relayProperties(
            batchSize = 7,
            publisherId = "story-service-1",
            publishingVisibilityTimeout = Duration.ofSeconds(30),
            retry = StoryTestFixture.retryProperties(
                maxAttempts = 3,
                baseDelay = Duration.ofSeconds(2),
                maxDelay = Duration.ofMinutes(2),
                multiplier = 3.0
            )
        ).toPolicy()

        assertEquals(7, policy.batchSize)
        assertEquals("story-service-1", policy.publisherId)
        assertEquals(Duration.ofSeconds(30), policy.publishingVisibilityTimeout)
        assertEquals(3, policy.retryPolicy.maxAttempts)
        assertEquals(Duration.ofSeconds(2), policy.retryPolicy.baseDelay)
        assertEquals(Duration.ofMinutes(2), policy.retryPolicy.maxDelay)
        assertEquals(3.0, policy.retryPolicy.multiplier)
    }
}
