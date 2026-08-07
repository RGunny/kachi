package me.rgunny.kachi.ai.application.port.out.llm

import me.rgunny.kachi.ai.application.port.dto.llm.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.dto.llm.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.dto.news.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * LLM provider 호출을 추상화하는 출력 포트
 */
interface LlmProviderPort {

    fun prepareNewsSummary(): PreparedLlmNewsSummary

    suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult

    suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult
}
