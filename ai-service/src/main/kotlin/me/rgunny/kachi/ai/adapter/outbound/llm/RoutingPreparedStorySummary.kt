package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmStorySummaryPlan
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmStorySummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmStorySummary
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreviousStorySummary
import me.rgunny.kachi.ai.application.port.outbound.llm.model.StorySummaryArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * 선조회에 쓴 plan을 들고 다니면서 story 요약 호출만 failover 순회로 처리하는 실행 단위.
 *
 * [plan]은 [order]의 첫 후보의 것이다.
 */
class RoutingPreparedStorySummary(
    override val plan: LlmStorySummaryPlan,
    private val router: RoutingLlmProvider,
    private val order: List<LlmProviderCandidate>
) : PreparedLlmStorySummary {

    override suspend fun summarize(
        keywords: List<AiKeyword>,
        previousSummary: PreviousStorySummary?,
        articles: List<StorySummaryArticle>
    ): LlmStorySummaryResult {
        return router.callWithFailover(order) { it.summarizeStory(keywords, previousSummary, articles) }
    }
}
