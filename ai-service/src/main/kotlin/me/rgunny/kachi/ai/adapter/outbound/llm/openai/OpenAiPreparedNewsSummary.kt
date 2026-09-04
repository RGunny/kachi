package me.rgunny.kachi.ai.adapter.outbound.llm.openai

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * 선조회에 쓴 provider/model/promptVersion과 실제 호출 대상을 묶어 두는 실행 단위.
 *
 * [plan]을 먼저 확정해야 application 계층이 LLM 호출 전에 기존 요약을 조회할 수 있다(ADR 011).
 * plan을 만든 adapter가 곧 호출 대상이므로 둘이 어긋날 수 없게 같은 객체가 들고 간다.
 */
internal class OpenAiPreparedNewsSummary(
    override val plan: LlmNewsSummaryPlan,
    private val provider: OpenAiLlmProvider
) : PreparedLlmNewsSummary {

    override suspend fun summarize(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        return provider.summarizeNews(keyword, articles)
    }
}
