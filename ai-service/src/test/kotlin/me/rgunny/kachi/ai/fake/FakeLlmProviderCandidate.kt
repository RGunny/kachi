package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.adapter.outbound.llm.LlmProviderCandidate
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailure
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
import java.time.Instant

/**
 * 가용성을 직접 지정할 수 있는 failover 후보 fake. 이름은 모델의 qualified code다.
 *
 * [blockedBy]에 사유를 넣으면 차단 상태가 된다. 차단 중에는 호출을 세지 않고
 * [LlmFailureCode.LLM_NOT_PERMITTED]로 실패한다. 회로나 cooldown이 어떻게 열리는지는 여기 관심이 아니다.
 */
class FakeLlmProviderCandidate(
    override val model: LlmModel
) : NamedLlmProviderPort(model.qualifiedCode, model.provider), LlmProviderCandidate {
    var blockedBy: String? = null

    override fun isLikelyAvailable(now: Instant): Boolean = blockedBy == null

    override fun blockedReason(now: Instant): String = blockedBy ?: NOT_BLOCKED

    override suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult {
        rejectIfBlocked()

        return super.expandKeyword(keyword, maxExpansions)
    }

    override suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        rejectIfBlocked()

        return super.summarizeNews(keyword, articles)
    }

    private fun rejectIfBlocked() {
        val reason = blockedBy ?: return

        throw LlmProviderException(
            LlmFailure(
                code = LlmFailureCode.LLM_NOT_PERMITTED,
                provider = model.provider,
                message = "${LlmFailureCode.LLM_NOT_PERMITTED.defaultMessage}: $reason"
            )
        )
    }

    private companion object {
        const val NOT_BLOCKED = "not-blocked"
    }
}
