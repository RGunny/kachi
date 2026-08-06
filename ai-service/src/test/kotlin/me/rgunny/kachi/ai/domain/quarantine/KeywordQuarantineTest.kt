package me.rgunny.kachi.ai.domain.quarantine

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("KeywordQuarantine")
class KeywordQuarantineTest {
    private val now = Instant.parse("2026-06-03T00:00:00Z")
    private val failureThreshold = 3

    @Test
    @DisplayName("실패할 때마다 연속 실패 횟수를 누적한다")
    fun accumulateConsecutiveFailures() {
        val tracked = track().recordFailure(AiFailureReason.TIMEOUT, failureThreshold, now)

        assertEquals(1, tracked.consecutiveFailures)
        assertEquals(AiFailureReason.TIMEOUT, tracked.lastFailureReason)
        assertEquals(KeywordQuarantineStatus.TRACKING, tracked.status)
        assertFalse(tracked.isQuarantined)
    }

    @Test
    @DisplayName("연속 실패가 임계치에 도달하면 격리한다")
    fun quarantineWhenThresholdReached() {
        val quarantined = failRepeatedly(failureThreshold)

        assertEquals(failureThreshold, quarantined.consecutiveFailures)
        assertEquals(KeywordQuarantineStatus.QUARANTINED, quarantined.status)
        assertTrue(quarantined.isQuarantined)
        assertEquals(now, quarantined.quarantinedAt)
    }

    @Test
    @DisplayName("성공하면 연속 실패 누적을 되돌려 임계치가 연속 실패에만 반응하게 한다")
    fun resetFailuresOnSuccess() {
        val reset = failRepeatedly(failureThreshold - 1).recordSuccess(now)

        assertEquals(0, reset.consecutiveFailures)
        assertNull(reset.lastFailureReason)
        assertEquals(KeywordQuarantineStatus.TRACKING, reset.status)
    }

    @Test
    @DisplayName("격리된 키워드는 실행 대상에서 빠지므로 실패가 다시 들어와도 격리 시점을 덮지 않는다")
    fun keepQuarantineStateOnLateFailure() {
        val quarantined = failRepeatedly(failureThreshold)

        val reFailed = quarantined.recordFailure(
            AiFailureReason.SERVER_ERROR,
            failureThreshold,
            now.plusSeconds(600)
        )

        assertEquals(now, reFailed.quarantinedAt)
        assertEquals(failureThreshold, reFailed.consecutiveFailures)
    }

    @Test
    @DisplayName("운영자가 격리를 해제하면 다시 요약 대상이 된다")
    fun release() {
        val released = failRepeatedly(failureThreshold).release(now.plusSeconds(600))

        assertEquals(KeywordQuarantineStatus.RELEASED, released.status)
        assertFalse(released.isQuarantined)
        assertEquals(0, released.consecutiveFailures)
        assertEquals(now.plusSeconds(600), released.releasedAt)
    }

    @Test
    @DisplayName("격리되지 않은 키워드는 해제할 수 없다")
    fun rejectReleaseWhenNotQuarantined() {
        assertFailsWith<IllegalArgumentException> {
            track().release(now)
        }
    }

    @Test
    @DisplayName("실패한 적 없는 기록은 저장이 필요 없다")
    fun needsResetOnlyWhenStateIsDirty() {
        assertFalse(track().needsReset())
        assertTrue(failRepeatedly(1).needsReset())
        assertTrue(failRepeatedly(failureThreshold).release(now).needsReset())
    }

    private fun track(): KeywordQuarantine {
        return KeywordQuarantine.track(
            targetType = AiRunTargetType.NEWS_SUMMARY,
            keyword = AiKeyword.of("NVIDIA"),
            updatedAt = now
        )
    }

    private fun failRepeatedly(times: Int): KeywordQuarantine {
        return (1..times).fold(track()) { quarantine, _ ->
            quarantine.recordFailure(AiFailureReason.TIMEOUT, failureThreshold, now)
        }
    }
}
