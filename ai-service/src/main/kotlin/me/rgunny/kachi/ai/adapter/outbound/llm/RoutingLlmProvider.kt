package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailure
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmUse
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

/**
 * 용도마다 정해진 후보 순서대로 모델을 호출하는 LLM 라우터.
 *
 * 구조는 컴포지트 패턴이다. [LlmProviderPort]를 구현하면서 같은 포트의 후보 여럿을 안에 품으므로, application 서비스는
 * 후보가 하나든 넷이든 같은 포트를 부르고 후보를 늘리거나 순서를 바꾸는 것은 설정 한 줄이다.
 * 묶은 후보를 쓰는 방식은 순차 failover 정책이다. 모든 후보가 처리 대상이고 실패했을 때만 다음으로 넘기므로,
 * handler가 처리 여부를 스스로 고르는 Chain of Responsibility와는 다르다. "라우터"는 역할 이름이지 패턴 이름이 아니다.
 *
 * 후보 하나가 실패하면 실패의 종류를 가리지 않고 다음으로 넘긴다. 입력 탓으로 보이는 실패도 넘긴다.
 * 한 모델의 거부가 그 모델 탓일 수 있어, 입력 탓인지는 시도한 후보 전부의 판정이 같을 때만 확정하고 그 판단은 소비처에 넘긴다.
 * 한 번의 호출에서 후보 하나는 최대 한 번만 시도하므로 호출 수는 후보 수로 묶인다.
 *
 * 순서는 설정 그대로다. 섞거나 가용성으로 정렬하지 않는다. 순서가 고정이어야 선조회 plan과 실제 첫 호출이 같다.
 * 여기서 정하는 것은 순회 정책뿐이다. 후보 하나의 호출 가능 여부는 [LlmProviderCandidate]가 판단한다.
 */
class RoutingLlmProvider(
    internal val candidates: Map<LlmUse, List<LlmProviderCandidate>>,
    private val clock: Clock
) : LlmProviderPort {

    init {
        LlmUse.entries.forEach { use ->
            require(!candidates[use].isNullOrEmpty()) { "LLM use ${use.name}의 후보가 없습니다" }
        }
    }

    /**
     * 호출 대상이 정해지기 전에 선조회 키가 필요하므로 첫 후보의 plan을 돌려준다.
     *
     * 후보가 전부 차단된 상태여도 여기서 실패시키지 않는다. 실패시키면 기존 요약으로 해결될 키워드까지
     * LLM 장애에 함께 묶인다. 실패는 실제 호출 시점에 난다.
     */
    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        val order = candidatesOf(LlmUse.NEWS_SUMMARY)

        return RoutingPreparedNewsSummary(
            plan = order.first().prepareNewsSummary().plan,
            router = this,
            order = order
        )
    }

    override suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult {
        return callWithFailover(candidatesOf(LlmUse.KEYWORD_EXPANSION)) { it.expandKeyword(keyword, maxExpansions) }
    }

    override suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        return callWithFailover(candidatesOf(LlmUse.NEWS_SUMMARY)) { it.summarizeNews(keyword, articles) }
    }

    /**
     * 후보를 순서대로 한 번씩만 호출하고, 처음 성공한 결과를 돌려준다.
     *
     * 후보를 모두 쓰고도 성공하지 못하면 실제로 호출된 실패가 있었는지로 결과를 가른다.
     * 차단은 호출이 아니므로, 차단이 마지막이었다는 이유로 "전 모델 불능"이라고 기록하면 거짓이 된다.
     * 실제 호출의 실패는 전부 예외에 실어 올린다. 격리 카운트는 그 전부의 책임이 어디 있는지를 보고 정한다.
     */
    internal suspend fun <T> callWithFailover(
        order: List<LlmProviderCandidate>,
        call: suspend (LlmProviderCandidate) -> T
    ): T {
        val failures = mutableListOf<LlmFailure>()
        var lastCallFailure: LlmProviderException? = null

        for ((index, candidate) in order.withIndex()) {
            try {
                return call(candidate)
            } catch (exception: LlmProviderException) {
                val failure = exception.failure

                // 1. 차단은 호출이 아니므로 "실제 실패"로 세지 않는다. 후보 소진 시 판정에 쓴다.
                failures += failure
                if (failure.fromActualCall) {
                    lastCallFailure = exception
                }

                // 2. 실패의 종류를 가리지 않고 다음 후보로 넘긴다.
                log.warn(
                    "LLM model call failed. Trying the next candidate: model={}, code={}, next={}",
                    candidate.model.qualifiedCode,
                    failure.code.code,
                    order.getOrNull(index + 1)?.model?.qualifiedCode ?: LlmFailure.NO_PROVIDER
                )
            }
        }

        // 3. 실제 호출이 있었으면 마지막 실패를 대표로 올린다. 제공자와 status가 실행 기록에 남아야 한다.
        lastCallFailure?.let { last ->
            log.warn(
                "All LLM candidates were tried without success: attempts=[{}]",
                failures.joinToString { "${it.providerCode}=${it.code.code}" }
            )
            throw LlmProviderException(
                failure = last.failure,
                cause = last,
                attempts = failures.filter { it.fromActualCall }
            )
        }

        throw noCandidateAvailable(order)
    }

    /**
     * 실제 호출이 한 건도 나가지 않은 실패.
     *
     * 어느 모델도 호출되지 않았으므로 특정 제공자를 적지 않는다.
     * 운영자에게 필요한 정보는 이름이 아니라 후보마다 왜 빠졌는가이므로 사유를 메시지에 열거한다.
     */
    private fun noCandidateAvailable(order: List<LlmProviderCandidate>): LlmProviderException {
        val now = Instant.now(clock)
        val reasons = order.joinToString { "${it.model.qualifiedCode}=${it.blockedReason(now)}" }
        val message = "no llm candidate available: $reasons"

        log.error(message)

        return LlmProviderException(
            LlmFailure(
                code = LlmFailureCode.LLM_NOT_PERMITTED,
                provider = null,
                message = message
            )
        )
    }

    private fun candidatesOf(use: LlmUse): List<LlmProviderCandidate> = candidates.getValue(use)

    private companion object {
        val log = LoggerFactory.getLogger(RoutingLlmProvider::class.java)
    }
}
