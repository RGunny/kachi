package me.rgunny.kachi.ai.adapter.`in`.web

import me.rgunny.kachi.ai.application.port.dto.keyword.ExpandKeywordsResult
import java.time.Instant

data class KeywordExpansionRunResponse(
    val id: String,
    val targetType: String,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val requestedKeywords: Int,
    val succeededCount: Int,
    val failureCount: Int
) {
    companion object {
        fun from(result: ExpandKeywordsResult): KeywordExpansionRunResponse {
            return KeywordExpansionRunResponse(
                id = result.runId.value.toString(),
                targetType = KEYWORD_EXPANSION_TARGET_TYPE,
                status = result.status.name,
                startedAt = result.startedAt,
                finishedAt = result.finishedAt,
                requestedKeywords = result.requestedKeywords,
                succeededCount = result.succeededCount,
                failureCount = result.failureCount
            )
        }

        private const val KEYWORD_EXPANSION_TARGET_TYPE = "KEYWORD_EXPANSION"
    }
}
