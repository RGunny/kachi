package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmStorySummaryPlan
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmStorySummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmStorySummary
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreviousStorySummary
import me.rgunny.kachi.ai.application.port.outbound.llm.model.StorySummaryArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * plan과 실제 호출 대상을 묶는 PreparedLlmStorySummary fake.
 *
 * [summarize]는 [provider]의 story 요약에 그대로 위임한다.
 */
class FakePreparedStorySummary(
    override val plan: LlmStorySummaryPlan,
    private val provider: LlmProviderPort
) : PreparedLlmStorySummary {

    override suspend fun summarize(
        keywords: List<AiKeyword>,
        previousSummary: PreviousStorySummary?,
        articles: List<StorySummaryArticle>
    ): LlmStorySummaryResult {
        return provider.summarizeStory(keywords, previousSummary, articles)
    }
}
