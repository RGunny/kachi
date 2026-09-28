package me.rgunny.kachi.ai.domain.quarantine

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryQuarantine")
class StoryQuarantineTest {

    private val now = AiTestFixture.NOW

    @Test
    @DisplayName("연속 실패가 임계치에 도달하면 격리된다")
    fun quarantineAtThreshold() {
        val quarantine = AiTestFixture.storyQuarantine(consecutiveFailures = 3, failureThreshold = 3)

        assertTrue(quarantine.isQuarantined)
        assertEquals(3, quarantine.consecutiveFailures)
        assertEquals(now, quarantine.quarantinedAt)
    }

    @Test
    @DisplayName("성공 한 번으로 연속 실패가 0이 된다")
    fun resetOnSuccess() {
        val quarantine = AiTestFixture.storyQuarantine(consecutiveFailures = 2)

        val reset = quarantine.recordSuccess(now)

        assertEquals(0, reset.consecutiveFailures)
        assertFalse(reset.isQuarantined)
    }

    @Test
    @DisplayName("격리된 기록은 추가 실패에 변하지 않는다")
    fun quarantinedRecordIsImmutableToFailures() {
        val quarantine = AiTestFixture.storyQuarantine(consecutiveFailures = 3)

        val recorded = quarantine.recordFailure(AiFailureReason.TIMEOUT, failureThreshold = 3, updatedAt = now.plusSeconds(60))

        assertEquals(quarantine.consecutiveFailures, recorded.consecutiveFailures)
        assertEquals(quarantine.quarantinedAt, recorded.quarantinedAt)
    }

    @Test
    @DisplayName("격리된 기록만 해제할 수 있고 해제되면 실패 누적이 0이다")
    fun releaseOnlyQuarantined() {
        val released = AiTestFixture.storyQuarantine(consecutiveFailures = 3).release(now.plusSeconds(60))

        assertEquals(StoryQuarantineStatus.RELEASED, released.status)
        assertEquals(0, released.consecutiveFailures)
        assertEquals(now.plusSeconds(60), released.releasedAt)
        assertFailsWith<IllegalArgumentException> {
            AiTestFixture.storyQuarantine(consecutiveFailures = 1).release(now)
        }
    }

    @Test
    @DisplayName("실패한 적 없는 추적 기록만 저장을 건너뛴다")
    fun needsResetOnlyWhenDirty() {
        assertFalse(AiTestFixture.storyQuarantine(consecutiveFailures = 0).needsReset())
        assertTrue(AiTestFixture.storyQuarantine(consecutiveFailures = 1).needsReset())
    }
}
