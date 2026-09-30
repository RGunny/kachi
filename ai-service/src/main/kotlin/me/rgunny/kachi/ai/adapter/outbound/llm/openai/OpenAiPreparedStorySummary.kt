package me.rgunny.kachi.ai.adapter.outbound.llm.openai

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmStorySummaryPlan
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmStorySummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmStorySummary
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreviousStorySummary
import me.rgunny.kachi.ai.application.port.outbound.llm.model.StorySummaryArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * 선조회에 쓴 provider/model/promptVersion과 실제 호출 대상을 묶어 두는 story 요약 실행 단위.
 *
 * [plan]과 [adapter]는 같은 모델의 것이다.
 */
class OpenAiPreparedStorySummary(
    override val plan: LlmStorySummaryPlan,
    private val adapter: OpenAiChatAdapter
) : PreparedLlmStorySummary {

    override suspend fun summarize(
        keywords: List<AiKeyword>,
        previousSummary: PreviousStorySummary?,
        articles: List<StorySummaryArticle>
    ): LlmStorySummaryResult {
        return adapter.summarizeStory(keywords, previousSummary, articles)
    }
}
