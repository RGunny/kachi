package me.rgunny.kachi.ai.application.port.out.llm

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.application.port.out.news.NewsArticle

/**
 * LLM provider 호출을 추상화하는 출력 포트
 */
interface LlmProviderPort {

    suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult

    suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult
}
