package me.rgunny.kachi.ai.application.port.`in`.keyword

import me.rgunny.kachi.ai.domain.AiRunId
import me.rgunny.kachi.ai.domain.AiRunStatus
import java.time.Instant

/**
 * 키워드 확장 실행 결과
 */
data class ExpandKeywordsResult(
    val runId: AiRunId,
    val status: AiRunStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val requestedKeywords: Int,
    val succeededCount: Int,
    val failureCount: Int
)
