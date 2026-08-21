package me.rgunny.kachi.ai.application.port.inbound.keyword.model

import me.rgunny.kachi.ai.domain.run.AiRunId
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunStatus
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
) {
    companion object {
        fun from(aiRun: AiRun): ExpandKeywordsResult {
            return ExpandKeywordsResult(
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
