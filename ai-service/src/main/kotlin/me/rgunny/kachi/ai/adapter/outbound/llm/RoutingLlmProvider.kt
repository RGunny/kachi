package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.config.LlmProviderMode
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import kotlin.random.Random

/**
 * enabled provider 목록을 mode 정책에 따라 호출하는 LLM provider router.
 */
class RoutingLlmProvider(
    private val providers: List<LlmProviderPort>,
    private val mode: LlmProviderMode,
    private val random: Random = Random.Default
) : LlmProviderPort {

    init {
        require(providers.isNotEmpty()) { "At least one LLM provider must be enabled" }
    }

    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        return when (mode) {
            LlmProviderMode.SINGLE_RANDOM -> randomProvider().prepareNewsSummary()
            LlmProviderMode.AGGREGATE -> throw UnsupportedOperationException("aggregate LLM provider mode is not implemented yet")
        }
    }

    override suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult {
        return when (mode) {
            LlmProviderMode.SINGLE_RANDOM -> randomProvider().expandKeyword(keyword, maxExpansions)
            LlmProviderMode.AGGREGATE -> throw UnsupportedOperationException("aggregate LLM provider mode is not implemented yet")
        }
    }

    override suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        return when (mode) {
            LlmProviderMode.SINGLE_RANDOM -> randomProvider().summarizeNews(keyword, articles)
            LlmProviderMode.AGGREGATE -> throw UnsupportedOperationException("aggregate LLM provider mode is not implemented yet")
        }
    }

    private fun randomProvider(): LlmProviderPort {
        return providers.random(random)
    }
}
