package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * 선조회에 쓴 plan은 위임 대상의 것을 그대로 쓰고, 호출만 차단 판정을 거치게 하는 실행 단위.
 */
internal class GuardedPreparedNewsSummary(
    override val plan: LlmNewsSummaryPlan,
    private val provider: GuardedLlmProvider
) : PreparedLlmNewsSummary {

    override suspend fun summarize(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        return provider.summarizeNews(keyword, articles)
    }
}
