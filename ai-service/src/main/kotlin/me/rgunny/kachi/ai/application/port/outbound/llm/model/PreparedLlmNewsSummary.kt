package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * 사전 조회에 사용한 provider/model/promptVersion과 실제 LLM 호출 대상을 일치시키는 실행 단위.
 */
interface PreparedLlmNewsSummary {
    val plan: LlmNewsSummaryPlan

    suspend fun summarize(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult
}
