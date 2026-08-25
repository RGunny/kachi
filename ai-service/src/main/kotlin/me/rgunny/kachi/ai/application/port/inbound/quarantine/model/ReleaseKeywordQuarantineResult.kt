package me.rgunny.kachi.ai.application.port.inbound.quarantine.model

import java.time.Instant

data class ReleaseKeywordQuarantineResult(
    val quarantine: KeywordQuarantineSummary,
    val releasedAt: Instant
)
