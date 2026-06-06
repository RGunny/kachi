package me.rgunny.kachi.ai.application.port.`in`.news

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.run.AiRunId
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunStatus
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import me.rgunny.kachi.ai.domain.summary.NewsSummaryId
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import java.time.Instant

data class SummarizedNewsResult(
    val id: NewsSummaryId,
    val keyword: AiKeyword,
    val title: String,
    val sentiment: NewsSummarySentiment,
    val reused: Boolean
) {
    companion object {
        fun from(
            summary: NewsSummary,
            reused: Boolean
        ): SummarizedNewsResult {
            return SummarizedNewsResult(
                id = summary.id,
                keyword = summary.keyword,
                title = summary.title,
                sentiment = summary.sentiment,
                reused = reused
            )
        }
    }
}

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
    val failureCount: Int,
    val summaries: List<SummarizedNewsResult>
) {
    companion object {
        fun from(
            aiRun: AiRun,
            summaries: List<SummarizedNewsResult> = emptyList()
        ): SummarizeNewsResult {
            return SummarizeNewsResult(
                runId = aiRun.id,
                status = aiRun.status,
                startedAt = aiRun.startedAt,
                finishedAt = aiRun.finishedAt,
                requestedKeywords = aiRun.requestedKeywords,
                succeededCount = aiRun.succeededCount,
                failureCount = aiRun.failureCount,
                summaries = summaries
            )
        }
    }
}
