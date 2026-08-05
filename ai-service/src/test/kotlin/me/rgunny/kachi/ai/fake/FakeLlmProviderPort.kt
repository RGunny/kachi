package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.out.llm.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.out.llm.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.out.llm.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.out.llm.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.out.news.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.fixture.AiTestFixture

/**
 * 키워드 확장과 뉴스 요약을 모두 지원하는 LLM provider fake.
 *
 * failedKeywords에 담긴 키워드는 두 호출 모두 실패시켜 부분 실패 시나리오를 만든다.
 */
class FakeLlmProviderPort : LlmProviderPort {
    var expandCallCount: Int = 0
    var summarizeCallCount: Int = 0
    var failedKeywords: Set<AiKeyword> = emptySet()

    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        return object : PreparedLlmNewsSummary {
            override val plan: LlmNewsSummaryPlan = LlmNewsSummaryPlan(
                provider = AiTestFixture.PROVIDER,
                model = AiTestFixture.MODEL,
                promptVersion = AiTestFixture.NEWS_SUMMARY_PROMPT_VERSION
            )

            override suspend fun summarize(
                keyword: AiKeyword,
                articles: List<NewsArticle>
            ): LlmNewsSummaryResult {
                return this@FakeLlmProviderPort.summarizeNews(keyword, articles)
            }
        }
    }

    override suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult {
        expandCallCount += 1
        failIfRequested(keyword)

        return LlmKeywordExpansionResult(
            expandedKeywords = listOf(
                ExpandedKeyword.of("AI 반도체"),
                ExpandedKeyword.of("GPU")
            ).take(maxExpansions),
            metadata = AiTestFixture.keywordExpansionMetadata()
        )
    }

    override suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        summarizeCallCount += 1
        failIfRequested(keyword)

        return LlmNewsSummaryResult(
            title = "${keyword.value} 요약",
            content = "요약 본문",
            sentiment = NewsSummarySentiment.NEUTRAL,
            metadata = AiTestFixture.newsSummaryMetadata()
        )
    }

    private fun failIfRequested(keyword: AiKeyword) {
        if (keyword in failedKeywords) {
            throw IllegalStateException("LLM failure")
        }
    }
}
