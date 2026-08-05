package me.rgunny.kachi.ai.adapter.out.llm

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.out.llm.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.out.llm.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.out.llm.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.out.llm.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.out.llm.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.out.news.NewsArticle
import me.rgunny.kachi.ai.config.LlmProviderMode
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("RoutingLlmProvider")
class RoutingLlmProviderTest {

    @Test
    @DisplayName("single-random mode는 등록된 provider 중 하나를 호출한다")
    fun callSingleRandomProvider() = runBlocking {
        val providers = listOf(
            NamedLlmProviderPort("openrouter"),
            NamedLlmProviderPort("groq"),
            NamedLlmProviderPort("mistral")
        )
        val router = RoutingLlmProvider(
            providers = providers,
            mode = LlmProviderMode.SINGLE_RANDOM,
            random = Random(1)
        )

        val result = router.expandKeyword(
            keyword = AiKeyword.of("NVIDIA"),
            maxExpansions = 3
        )

        assertEquals(1, providers.count { it.expandCallCount == 1 })
        assertEquals(result.metadata.provider.value, providers.first { it.expandCallCount == 1 }.provider)
    }

    @Test
    @DisplayName("provider가 없으면 생성할 수 없다")
    fun failWhenProvidersAreEmpty() {
        assertFailsWith<IllegalArgumentException> {
            RoutingLlmProvider(
                providers = emptyList(),
                mode = LlmProviderMode.SINGLE_RANDOM
            )
        }
    }

    @Test
    @DisplayName("aggregate mode는 아직 호출하지 않는다")
    fun aggregateModeIsNotImplemented() = runBlocking {
        val router = RoutingLlmProvider(
            providers = listOf(NamedLlmProviderPort("openrouter")),
            mode = LlmProviderMode.AGGREGATE
        )

        assertFailsWith<UnsupportedOperationException> {
            router.expandKeyword(
                keyword = AiKeyword.of("NVIDIA"),
                maxExpansions = 3
            )
        }
    }

    private class NamedLlmProviderPort(
        val provider: String
    ) : LlmProviderPort {
        var expandCallCount: Int = 0

        override fun prepareNewsSummary(): PreparedLlmNewsSummary {
            return object : PreparedLlmNewsSummary {
                override val plan: LlmNewsSummaryPlan = LlmNewsSummaryPlan(
                    provider = LlmProviderName.of(provider),
                    model = LlmModelName.of("test-model"),
                    promptVersion = PromptVersion.of("news-summary-v1")
                )

                override suspend fun summarize(
                    keyword: AiKeyword,
                    articles: List<NewsArticle>
                ): LlmNewsSummaryResult {
                    return this@NamedLlmProviderPort.summarizeNews(keyword, articles)
                }
            }
        }

        override suspend fun expandKeyword(
            keyword: AiKeyword,
            maxExpansions: Int
        ): LlmKeywordExpansionResult {
            expandCallCount += 1

            return LlmKeywordExpansionResult(
                expandedKeywords = listOf(ExpandedKeyword.of("AI 반도체")),
                metadata = metadata(PromptVersion.of("keyword-expansion-v1"))
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
                metadata = metadata(PromptVersion.of("news-summary-v1"))
            )
        }

        private fun metadata(promptVersion: PromptVersion): LlmGenerationMetadata {
            return LlmGenerationMetadata(
                provider = LlmProviderName.of(provider),
                model = LlmModelName.of("test-model"),
                promptVersion = promptVersion,
                tokenUsage = TokenUsage(inputTokens = 1, outputTokens = 1)
            )
        }
    }
}
