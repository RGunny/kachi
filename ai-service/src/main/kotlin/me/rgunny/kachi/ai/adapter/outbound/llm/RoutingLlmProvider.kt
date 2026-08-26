package me.rgunny.kachi.ai.adapter.outbound.llm

import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.config.LlmProviderMode
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailure
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import kotlin.random.Random

/**
 * enabled provider 목록을 mode 정책에 따라 호출하는 LLM provider router.
 *
 * provider 하나가 실패해도 다른 provider가 같은 요청을 처리할 수 있으면 다음 후보로 넘긴다.
 * 판별 기준은 "다른 provider가 같은 입력으로 성공할 수 있는가"이고, 그 답이 곧 실패의 keyword 귀속 여부다.
 * 한 번의 호출에서 provider 하나는 최대 한 번만 시도하므로 호출 수는 enabled provider 수로 묶인다.
 *
 * 여기서 정하는 것은 순회 정책뿐이다. provider 하나의 호출 가능 여부는 [LlmProviderCandidate]가 판단한다.
 */
class RoutingLlmProvider(
    internal val providers: List<LlmProviderCandidate>,
    private val mode: LlmProviderMode,
    private val clock: Clock,
    private val random: Random = Random.Default
) : LlmProviderPort {

    init {
        require(providers.isNotEmpty()) { "At least one LLM provider must be enabled" }
    }

    /**
     * 호출 대상이 정해지기 전에 선조회 키가 필요하므로 첫 후보의 plan을 돌려준다.
     *
     * 후보가 전부 차단된 상태여도 여기서 실패시키지 않는다. 실패시키면 기존 요약으로 해결될 키워드까지
     * LLM 장애에 함께 묶인다. 실패는 실제 호출 시점에 난다.
     */
    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        return when (mode) {
            LlmProviderMode.SINGLE_RANDOM -> {
                val order = candidateOrder()

                RoutingPreparedNewsSummary(
                    plan = order.first().prepareNewsSummary().plan,
                    router = this,
                    order = order
                )
            }

            LlmProviderMode.AGGREGATE -> throw UnsupportedOperationException("aggregate LLM provider mode is not implemented yet")
        }
    }

    override suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult {
        return when (mode) {
            LlmProviderMode.SINGLE_RANDOM -> callWithFailover(candidateOrder()) {
                it.expandKeyword(keyword, maxExpansions)
            }

            LlmProviderMode.AGGREGATE -> throw UnsupportedOperationException("aggregate LLM provider mode is not implemented yet")
        }
    }

    override suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        return when (mode) {
            LlmProviderMode.SINGLE_RANDOM -> callWithFailover(candidateOrder()) {
                it.summarizeNews(keyword, articles)
            }

            LlmProviderMode.AGGREGATE -> throw UnsupportedOperationException("aggregate LLM provider mode is not implemented yet")
        }
    }

    /**
     * 후보를 순서대로 한 번씩만 호출하고, 처음 성공한 결과를 돌려준다.
     *
     * 후보를 모두 쓰고도 성공하지 못하면 실제로 호출된 실패가 있었는지로 결과를 가른다.
     * 차단은 호출이 아니므로, 차단이 마지막이었다는 이유로 "전 provider 불능"이라고 기록하면 거짓이 된다.
     */
    internal suspend fun <T> callWithFailover(
        order: List<LlmProviderCandidate>,
        call: suspend (LlmProviderCandidate) -> T
    ): T {
        val failures = mutableListOf<LlmFailure>()
        var lastCallFailure: LlmProviderException? = null

        for ((index, provider) in order.withIndex()) {
            try {
                return call(provider)
            } catch (exception: LlmProviderException) {
                val failure = exception.failure

                // 1. 같은 입력이면 다른 provider도 같은 결과를 준다. 넘겨도 호출만 늘어난다.
                if (failure.keywordBound) {
                    throw exception
                }

                // 2. 차단은 호출이 아니므로 "실제 실패"로 세지 않는다. 후보 소진 시 판정에 쓴다.
                failures += failure
                if (failure.fromActualCall) {
                    lastCallFailure = exception
                }

                // 3. 다음 후보로 넘긴다.
                log.warn(
                    "LLM provider call failed. Trying the next provider: provider={}, code={}, next={}",
                    provider.provider.value,
                    failure.code.code,
                    order.getOrNull(index + 1)?.provider?.value ?: LlmProviderName.NONE.value
                )
            }
        }

        // 4. 실제 호출이 있었으면 그 실패를 그대로 올린다. provider와 status가 실행 기록에 남아야 한다.
        lastCallFailure?.let {
            log.warn(
                "All LLM providers were tried without success: attempts=[{}]",
                failures.joinToString { "${it.provider.value}=${it.code.code}" }
            )
            throw it
        }

        throw noProviderAvailable(order)
    }

    /**
     * 실제 호출이 한 건도 나가지 않은 실패.
     *
     * 어느 provider도 호출되지 않았으므로 특정 provider 이름을 쓰지 않는다.
     * 운영자에게 필요한 정보는 이름이 아니라 provider마다 왜 빠졌는가이므로 사유를 메시지에 열거한다.
     */
    private fun noProviderAvailable(order: List<LlmProviderCandidate>): LlmProviderException {
        val now = Instant.now(clock)
        val reasons = order.joinToString { "${it.provider.value}=${it.blockedReason(now)}" }
        val message = "no llm provider available: $reasons"

        log.error(message)

        return LlmProviderException(
            LlmFailure(
                code = LlmFailureCode.LLM_PROVIDER_UNAVAILABLE,
                provider = LlmProviderName.NONE,
                message = message
            )
        )
    }

    /**
     * 무작위로 섞은 뒤 호출 가능해 보이는 provider를 앞으로 보낸다.
     *
     * 정렬은 힌트일 뿐이라 틀려도 무해하다. 첫 후보가 실제 호출 대상과 일치할 확률을 높여
     * 선조회 키가 빗나가는 경우를 줄이려는 것이다(ADR 011).
     */
    private fun candidateOrder(): List<LlmProviderCandidate> {
        val now = Instant.now(clock)

        return providers.shuffled(random).sortedBy { !it.isLikelyAvailable(now) }
    }

    private companion object {
        val log = LoggerFactory.getLogger(RoutingLlmProvider::class.java)
    }
}
