package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * 선조회에 쓴 plan을 들고 다니면서 요약 호출만 failover 순회로 처리하는 실행 단위.
 *
 * [plan]은 [order]의 첫 후보에서 나온다.
 * 선조회 키는 promptVersion만 쓰므로 failover로 다른 모델이 요약해도 키는 그대로다.
 */
internal class RoutingPreparedNewsSummary(
    override val plan: LlmNewsSummaryPlan,
    private val router: RoutingLlmProvider,
    private val order: List<LlmProviderCandidate>
) : PreparedLlmNewsSummary {

    override suspend fun summarize(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        return router.callWithFailover(order) { it.summarizeNews(keyword, articles) }
    }
}
