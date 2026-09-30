package me.rgunny.kachi.ai.application.port.inbound.story.model

/**
 * 요약을 실행하지 않고 끝낸 결과.
 */
data class SkippedStorySummaryResult(
    val reason: StorySummarySkipReason
) : SummarizeStoryResult
