package me.rgunny.kachi.ai.domain.quarantine

import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.story.StoryId
import java.time.Instant

/**
 * story별 연속 실패 누적과 격리 상태.
 *
 * [status]가 QUARANTINED이면 실패·성공 기록은 상태를 바꾸지 않는다.
 * [quarantinedAt]과 [releasedAt]은 마지막 격리·해제 시각이다.
 */
class StoryQuarantine private constructor(
    val id: StoryQuarantineId,
    val storyId: StoryId,
    val consecutiveFailures: Int,
    val lastFailureReason: AiFailureReason?,
    val status: StoryQuarantineStatus,
    val quarantinedAt: Instant?,
    val releasedAt: Instant?,
    val updatedAt: Instant
) {
    val isQuarantined: Boolean
        get() = status == StoryQuarantineStatus.QUARANTINED

    /**
     * 실패를 누적하고, 임계치에 도달하면 격리한다.
     */
    fun recordFailure(
        reason: AiFailureReason,
        failureThreshold: Int,
        updatedAt: Instant
    ): StoryQuarantine {
        require(failureThreshold >= 1) { "격리 임계치는 1 이상이어야 합니다" }

        // 격리 후 실패 기록 무시(격리 시각 유지)
        if (isQuarantined) {
            return this
        }

        val failures = consecutiveFailures + 1
        val quarantined = failures >= failureThreshold

        return StoryQuarantine(
            id = id,
            storyId = storyId,
            consecutiveFailures = failures,
            lastFailureReason = reason,
            status = if (quarantined) StoryQuarantineStatus.QUARANTINED else StoryQuarantineStatus.TRACKING,
            // 마지막 격리 시각(현재 상태는 status)
            quarantinedAt = if (quarantined) updatedAt else quarantinedAt,
            releasedAt = releasedAt,
            updatedAt = updatedAt
        )
    }

    /**
     * 연속 실패 누적을 0으로 되돌린다.
     *
     * 격리 상태면 아무것도 바꾸지 않는다.
     */
    fun recordSuccess(updatedAt: Instant): StoryQuarantine {
        if (isQuarantined) {
            return this
        }

        return StoryQuarantine(
            id = id,
            storyId = storyId,
            consecutiveFailures = 0,
            lastFailureReason = null,
            status = StoryQuarantineStatus.TRACKING,
            quarantinedAt = quarantinedAt,
            releasedAt = releasedAt,
            updatedAt = updatedAt
        )
    }

    /**
     * 격리를 해제한다.
     *
     * 격리 상태가 아니면 [IllegalArgumentException]을 던진다.
     */
    fun release(updatedAt: Instant): StoryQuarantine {
        require(isQuarantined) { "격리된 story만 해제할 수 있습니다" }

        return StoryQuarantine(
            id = id,
            storyId = storyId,
            consecutiveFailures = 0,
            lastFailureReason = lastFailureReason,
            status = StoryQuarantineStatus.RELEASED,
            quarantinedAt = quarantinedAt,
            releasedAt = updatedAt,
            updatedAt = updatedAt
        )
    }

    /**
     * 되돌릴 실패 누적이나 TRACKING이 아닌 상태가 남아 있는지 돌려준다.
     */
    fun needsReset(): Boolean {
        return consecutiveFailures > 0 || status != StoryQuarantineStatus.TRACKING
    }

    companion object {

        fun track(
            storyId: StoryId,
            updatedAt: Instant
        ): StoryQuarantine {
            return StoryQuarantine(
                id = StoryQuarantineId.newId(),
                storyId = storyId,
                consecutiveFailures = 0,
                lastFailureReason = null,
                status = StoryQuarantineStatus.TRACKING,
                quarantinedAt = null,
                releasedAt = null,
                updatedAt = updatedAt
            )
        }

        fun restore(
            id: StoryQuarantineId,
            storyId: StoryId,
            consecutiveFailures: Int,
            lastFailureReason: AiFailureReason?,
            status: StoryQuarantineStatus,
            quarantinedAt: Instant?,
            releasedAt: Instant?,
            updatedAt: Instant
        ): StoryQuarantine {
            return StoryQuarantine(
                id = id,
                storyId = storyId,
                consecutiveFailures = consecutiveFailures,
                lastFailureReason = lastFailureReason,
                status = status,
                quarantinedAt = quarantinedAt,
                releasedAt = releasedAt,
                updatedAt = updatedAt
            )
        }
    }
}
