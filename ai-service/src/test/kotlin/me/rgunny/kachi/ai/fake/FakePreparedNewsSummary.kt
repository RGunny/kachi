package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * plan과 실제 호출 대상을 묶는 PreparedLlmNewsSummary fake.
 *
 * 선조회에 쓴 plan과 호출 대상이 어긋나면 재사용 테스트가 의미를 잃으므로 같은 provider를 그대로 위임한다.
 */
class FakePreparedNewsSummary(
    override val plan: LlmNewsSummaryPlan,
    private val provider: LlmProviderPort
) : PreparedLlmNewsSummary {

    override suspend fun summarize(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        return provider.summarizeNews(keyword, articles)
    }
}
