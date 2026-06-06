package me.rgunny.kachi.ai.adapter.`in`.web

import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsResult
import java.time.Instant

data class NewsSummaryRunResponse(
    val id: String,
    val targetType: String,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val requestedKeywords: Int,
    val succeededCount: Int,
    val failureCount: Int,
    val summaries: List<NewsSummaryResponse>
) {
    companion object {
        fun from(result: SummarizeNewsResult): NewsSummaryRunResponse {
            return NewsSummaryRunResponse(
                id = result.runId.value.toString(),
                targetType = NEWS_SUMMARY_TARGET_TYPE,
                status = result.status.name,
                startedAt = result.startedAt,
                finishedAt = result.finishedAt,
                requestedKeywords = result.requestedKeywords,
                succeededCount = result.succeededCount,
                failureCount = result.failureCount,
                summaries = result.summaries.map {
                    NewsSummaryResponse(
                        id = it.id.value.toString(),
                        keyword = it.keyword.value,
                        title = it.title,
                        sentiment = it.sentiment.name,
                        reused = it.reused
                    )
                }
            )
        }

        private const val NEWS_SUMMARY_TARGET_TYPE = "NEWS_SUMMARY"
    }
}

data class NewsSummaryResponse(
    val id: String,
    val keyword: String,
    val title: String,
    val sentiment: String,
    val reused: Boolean
)
