package me.rgunny.kachi.ai.application.port.inbound.quarantine.model

/**
 * story 격리 기록 조회 결과.
 */
data class FindStoryQuarantinesResult(
    val quarantines: List<StoryQuarantineSnapshot>
)
