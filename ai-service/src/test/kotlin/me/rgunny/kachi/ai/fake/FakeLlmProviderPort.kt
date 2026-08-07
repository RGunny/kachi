package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.dto.llm.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.dto.llm.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.dto.llm.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.out.llm.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.dto.news.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.fixture.AiTestFixture

/**
 * 키워드 확장과 뉴스 요약을 모두 지원하는 LLM provider fake.
 *
 * failedKeywords에 담긴 키워드는 두 호출 모두 실패시켜 부분 실패 시나리오를 만든다.
 * 실패 분류까지 확인해야 하면 failureByKeyword에 예외를 직접 담는다.
 */
class FakeLlmProviderPort : LlmProviderPort {
    var expandCallCount: Int = 0
    var summarizeCallCount: Int = 0
    var failedKeywords: Set<AiKeyword> = emptySet()
    var failureByKeyword: Map<AiKeyword, Throwable> = emptyMap()

    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        return FakePreparedNewsSummary(
            plan = LlmNewsSummaryPlan(
                provider = AiTestFixture.PROVIDER,
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
        failureByKeyword[keyword]?.let { throw it }

        if (keyword in failedKeywords) {
            throw IllegalStateException("LLM failure")
        }
    }
}
