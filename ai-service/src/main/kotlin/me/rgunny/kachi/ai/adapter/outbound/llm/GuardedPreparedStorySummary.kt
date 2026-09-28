package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmStorySummaryPlan
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmStorySummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmStorySummary
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreviousStorySummary
import me.rgunny.kachi.ai.application.port.outbound.llm.model.StorySummaryArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * 선조회에 쓴 plan은 위임 대상의 것을 그대로 쓰고, 호출만 차단 판정을 거치게 하는 story 요약 실행 단위.
 */
class GuardedPreparedStorySummary(
    override val plan: LlmStorySummaryPlan,
    private val model: GuardedLlmModel
) : PreparedLlmStorySummary {

    override suspend fun summarize(
        keywords: List<AiKeyword>,
        previousSummary: PreviousStorySummary?,
        articles: List<StorySummaryArticle>
    ): LlmStorySummaryResult {
        return model.summarizeStory(keywords, previousSummary, articles)
    }
}
