package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.dto.llm.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.dto.llm.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.dto.llm.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.dto.llm.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.dto.news.NewsArticle
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.out.llm.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment

/**
 * 어느 provider가 호출됐는지 이름으로 식별할 수 있는 LLM provider fake.
 *
 * 라우팅과 failover 테스트는 "몇 번 호출됐는가"가 아니라 "누가 호출됐는가"를 확인해야 한다.
 */
class NamedLlmProviderPort(
    val provider: String
) : LlmProviderPort {
    var expandCallCount: Int = 0

    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        return FakePreparedNewsSummary(
            plan = LlmNewsSummaryPlan(
                provider = LlmProviderName.of(provider),
                model = LlmModelName.of(MODEL),
                promptVersion = PromptVersion.of(NEWS_SUMMARY_PROMPT_VERSION)
            ),
            provider = this
        )
    }

    override suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult {
        expandCallCount += 1

        return LlmKeywordExpansionResult(
            expandedKeywords = listOf(ExpandedKeyword.of("AI 반도체")),
            metadata = metadata(PromptVersion.of(KEYWORD_EXPANSION_PROMPT_VERSION))
        )
    }

    override suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        return LlmNewsSummaryResult(
            title = "요약",
            content = "본문",
            sentiment = NewsSummarySentiment.UNKNOWN,
            metadata = metadata(PromptVersion.of(NEWS_SUMMARY_PROMPT_VERSION))
        )
    }

    private fun metadata(promptVersion: PromptVersion): LlmGenerationMetadata {
        return LlmGenerationMetadata(
            provider = LlmProviderName.of(provider),
            model = LlmModelName.of(MODEL),
            promptVersion = promptVersion,
            tokenUsage = TokenUsage(inputTokens = 1, outputTokens = 1)
        )
    }

    private companion object {
        const val MODEL = "test-model"
        const val NEWS_SUMMARY_PROMPT_VERSION = "news-summary-v1"
        const val KEYWORD_EXPANSION_PROMPT_VERSION = "keyword-expansion-v1"
    }
}
