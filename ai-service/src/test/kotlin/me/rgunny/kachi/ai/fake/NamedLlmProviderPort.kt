package me.rgunny.kachi.ai.fake

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.fixture.AiTestFixture
import java.time.Duration

/**
 * 어느 provider가 호출됐는지 이름으로 식별할 수 있는 LLM provider fake.
 *
 * 라우팅과 failover 테스트는 "몇 번 호출됐는가"가 아니라 "누가 호출됐는가"를 확인해야 한다.
 *
 * [failures]에 넣은 예외를 호출 순서대로 하나씩 던진다. 비면 성공한다.
 * [callDelay]는 느린 호출 판정을, [gate]는 여러 호출이 동시에 진행되는 상황을 만드는 데 쓴다.
 * [callLog]를 여러 fake가 공유하면 provider 사이의 호출 순서를 볼 수 있다.
 */
open class NamedLlmProviderPort(
    val name: String
) : LlmProviderPort {
    var expandCallCount: Int = 0
    var summarizeCallCount: Int = 0
    val failures: ArrayDeque<Throwable> = ArrayDeque()
    var callDelay: Duration = Duration.ZERO
    var gate: CompletableDeferred<Unit>? = null
    var callLog: MutableList<String> = mutableListOf()

    val callCount: Int
        get() = expandCallCount + summarizeCallCount

    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        return FakePreparedNewsSummary(
            plan = LlmNewsSummaryPlan(
                provider = LlmProviderName.of(name),
                model = AiTestFixture.MODEL,
                promptVersion = AiTestFixture.NEWS_SUMMARY_PROMPT_VERSION
            ),
            provider = this
        )
    }

    override suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult {
        expandCallCount += 1
        awaitCall()

        return LlmKeywordExpansionResult(
            expandedKeywords = listOf(ExpandedKeyword.of("AI 반도체")),
            metadata = metadata(AiTestFixture.KEYWORD_EXPANSION_PROMPT_VERSION)
        )
    }

    override suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        summarizeCallCount += 1
        awaitCall()

        return LlmNewsSummaryResult(
            title = "요약",
            content = "본문",
            sentiment = NewsSummarySentiment.UNKNOWN,
            metadata = metadata(AiTestFixture.NEWS_SUMMARY_PROMPT_VERSION)
        )
    }

    private suspend fun awaitCall() {
        callLog += name
        gate?.await()

        if (!callDelay.isZero) {
            delay(callDelay.toMillis())
        }

        failures.removeFirstOrNull()?.let { throw it }
    }

    private fun metadata(promptVersion: PromptVersion): LlmGenerationMetadata {
        return LlmGenerationMetadata(
            provider = LlmProviderName.of(name),
            model = AiTestFixture.MODEL,
            promptVersion = promptVersion,
            tokenUsage = TokenUsage(inputTokens = 1, outputTokens = 1)
        )
    }
}
