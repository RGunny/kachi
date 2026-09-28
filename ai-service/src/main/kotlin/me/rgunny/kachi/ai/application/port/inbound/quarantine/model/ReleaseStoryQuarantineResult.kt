package me.rgunny.kachi.ai.application.port.inbound.quarantine.model

import java.time.Instant

/**
 * story 격리 해제 결과.
 */
data class ReleaseStoryQuarantineResult(
    val quarantine: StoryQuarantineSnapshot,
    val releasedAt: Instant
)
