package me.rgunny.kachi.ai.application.port.inbound.story.model

import me.rgunny.kachi.ai.domain.run.AiFailureReason

/**
 * story 탓으로 판정된 요약 실패의 결과.
 *
 * [quarantined]는 이번 실패로 story가 격리 상태에 들어갔다는 뜻이다.
 */
data class FailedStorySummaryResult(
    val reason: AiFailureReason,
    val quarantined: Boolean
) : SummarizeStoryResult
