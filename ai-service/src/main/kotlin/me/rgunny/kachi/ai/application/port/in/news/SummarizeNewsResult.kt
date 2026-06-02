package me.rgunny.kachi.ai.application.port.`in`.news

import me.rgunny.kachi.ai.domain.AiRunId
import me.rgunny.kachi.ai.domain.AiRunStatus
import java.time.Instant

/**
 * 뉴스 요약 실행 결과
 */
data class SummarizeNewsResult(
    val runId: AiRunId,
    val status: AiRunStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val requestedKeywords: Int,
    val succeededCount: Int,
    val failureCount: Int
)
