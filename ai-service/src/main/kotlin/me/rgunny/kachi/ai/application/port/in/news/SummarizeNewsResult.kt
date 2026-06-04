package me.rgunny.kachi.ai.application.port.`in`.news

import me.rgunny.kachi.ai.domain.run.AiRunId
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunStatus
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
) {
    companion object {
        fun from(aiRun: AiRun): SummarizeNewsResult {
            return SummarizeNewsResult(
                runId = aiRun.id,
                status = aiRun.status,
                startedAt = aiRun.startedAt,
                finishedAt = aiRun.finishedAt,
                requestedKeywords = aiRun.requestedKeywords,
                succeededCount = aiRun.succeededCount,
                failureCount = aiRun.failureCount
            )
        }
    }
}
