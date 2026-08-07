package me.rgunny.kachi.ai.application.port.dto.news

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import me.rgunny.kachi.ai.domain.summary.NewsSummaryId
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment

/**
 * 요약 한 건의 실행 결과.
 *
 * reused는 이번 실행이 LLM을 호출했는지 기존 요약을 재사용했는지를 나타낸다(ADR 011).
 */
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
