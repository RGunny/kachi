package me.rgunny.kachi.ai.adapter.outbound.llm

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.domain.llm.LlmUse
import me.rgunny.kachi.ai.fake.FakeLlmProviderCandidate
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.MutableClock
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * 순회 정책만 검증한다. 후보가 왜 차단되는지는 후보 구현의 몫이라 가용성은 fake에 직접 지정한다.
 */
@DisplayName("RoutingLlmProvider")
class RoutingLlmProviderTest {
    private val clock = MutableClock()
    private val callLog = mutableListOf<String>()
    private val first = candidate(LlmModel.GROQ_QWEN3_27B)
    private val second = candidate(LlmModel.MISTRAL_SMALL_2603)
    private val third = candidate(LlmModel.OLLAMA_QWEN3_27B)
    private val router = router(
        summary = listOf(first, second),
        expansion = listOf(first, second)
    )

    @Test
    @DisplayName("후보를 설정 순서 그대로 시도한다")
    fun tryCandidatesInConfiguredOrder() = runBlocking {
        val router = router(summary = listOf(third, first, second), expansion = listOf(first))
        listOf(third, first).forEach { it.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT) }

        val result = router.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(listOf(third.name, first.name, second.name), callLog)
        assertEquals(second.name, result.metadata.model)
    }

    @Test
    @DisplayName("용도마다 자기 후보 목록을 쓴다")
    fun useCandidatesOfEachUse() = runBlocking {
        val router = router(summary = listOf(first), expansion = listOf(third))

        router.summarizeNews(KEYWORD, ARTICLES)
        router.expandKeyword(KEYWORD, 3)

        assertEquals(listOf(first.name, third.name), callLog)
    }

    @Test
    @DisplayName("후보가 없는 용도가 있으면 생성할 수 없다")
    fun failWhenAnyUseHasNoCandidates() {
        assertFailsWith<IllegalArgumentException> {
            RoutingLlmProvider(candidates = mapOf(LlmUse.NEWS_SUMMARY to listOf(first)), clock = clock)
        }
        assertFailsWith<IllegalArgumentException> {
            RoutingLlmProvider(
                candidates = mapOf(LlmUse.NEWS_SUMMARY to listOf(first), LlmUse.KEYWORD_EXPANSION to emptyList()),
                clock = clock
            )
        }
    }

    @Test
    @DisplayName("재시도 가능한 실패면 다음 후보를 호출한다")
    fun failoverOnRetryableFailure() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)

        val result = router.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(1, first.summarizeCallCount)
        assertEquals(1, second.summarizeCallCount)
        assertEquals(second.name, result.metadata.model)
    }

    @Test
    @DisplayName("키워드 귀속 실패는 다른 후보를 시도하지 않는다")
    fun doNotFailoverOnKeywordBoundFailure() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_INVALID_RESPONSE)

        val failure = assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_INVALID_RESPONSE, failure.failure.code)
        assertEquals(0, second.summarizeCallCount)
    }

    @Test
    @DisplayName("요청 검증 실패도 다른 후보를 시도하지 않는다")
    fun doNotFailoverOnValidationFailure() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_CLIENT_ERROR)

        val failure = assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_CLIENT_ERROR, failure.failure.code)
        assertEquals(0, second.summarizeCallCount)
    }

    @Test
    @DisplayName("인증 실패는 다른 후보로 넘어간다")
    fun failoverOnAuthorizationFailure() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_AUTHORIZATION_ERROR)

        val result = router.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(second.name, result.metadata.model)
    }

    @Test
    @DisplayName("분류되지 않은 LLM 실패는 다른 후보로 넘어간다")
    fun failoverOnUnknownFailure() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_UNKNOWN_ERROR)

        val result = router.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(second.name, result.metadata.model)
    }

    @Test
    @DisplayName("LLM 실패가 아닌 예외는 즉시 전파한다")
    fun propagateNonLlmException() = runBlocking {
        first.failures += IllegalArgumentException("boom")

        assertFailsWith<IllegalArgumentException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(0, second.summarizeCallCount)
    }

    @Test
    @DisplayName("각 후보는 한 호출에 최대 한 번만 시도한다")
    fun tryEachCandidateOnce() = runBlocking {
        val candidates = listOf(first, second, third)
        val router = router(summary = candidates, expansion = candidates)
        candidates.forEach { it.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT) }

        assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        candidates.forEach { assertEquals(1, it.summarizeCallCount, "${it.name} must be called once") }
        assertEquals(3, callLog.size)
    }

    @Test
    @DisplayName("차단된 후보는 호출 없이 건너뛴다")
    fun skipBlockedCandidateWithoutCall() = runBlocking {
        first.blockedBy = OPEN

        val result = router.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(0, first.summarizeCallCount)
        assertEquals(1, second.summarizeCallCount)
        assertEquals(second.name, result.metadata.model)
    }

    @Test
    @DisplayName("모든 후보가 차단되면 호출 없이 실패한다")
    fun failWithoutCallWhenAllCandidatesAreBlocked() = runBlocking {
        listOf(first, second).forEach { it.blockedBy = OPEN }

        val failure = assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, failure.failure.code)
        assertNull(failure.failure.provider)
        assertEquals(0, first.summarizeCallCount + second.summarizeCallCount)
        assertEquals(
            "no llm candidate available: ${first.name}=$OPEN, ${second.name}=$OPEN",
            failure.failure.message
        )
    }

    @Test
    @DisplayName("실제 호출이 있었으면 차단이 마지막이어도 실제 실패를 전파한다")
    fun propagateActualFailureWhenBlockedComesLast() = runBlocking {
        val thrown = AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)
        first.failures += thrown
        second.blockedBy = OPEN

        val failure = assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertSame(thrown, failure)
    }

    @Test
    @DisplayName("차단이 먼저였어도 실제 실패를 전파한다")
    fun propagateActualFailureWhenBlockedComesFirst() = runBlocking {
        first.blockedBy = OPEN
        second.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)

        val failure = assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_TIMEOUT, failure.failure.code)
        assertEquals(1, second.summarizeCallCount)
    }

    @Test
    @DisplayName("실제 호출이 여럿이면 마지막 실제 실패를 전파한다")
    fun propagateLastActualFailure() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)
        second.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_RATE_LIMITED)

        val failure = assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_RATE_LIMITED, failure.failure.code)
    }

    @Test
    @DisplayName("차단 사유가 후보마다 다르면 각각 열거한다")
    fun listBlockedReasonPerCandidate() = runBlocking {
        first.blockedBy = OPEN
        second.blockedBy = COOLDOWN

        val failure = assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(
            "no llm candidate available: ${first.name}=$OPEN, ${second.name}=$COOLDOWN",
            failure.failure.message
        )
    }

    @Test
    @DisplayName("요약 plan은 첫 후보의 것이고 그 후보를 먼저 호출한다")
    fun planComesFromFirstCandidate() = runBlocking {
        val prepared = router.prepareNewsSummary()
        prepared.summarize(KEYWORD, ARTICLES)

        assertEquals(first.model.provider, prepared.plan.provider)
        assertEquals(first.name, callLog.first())
    }

    @Test
    @DisplayName("첫 후보가 차단돼 있어도 plan은 첫 후보의 것이다")
    fun planIgnoresAvailability() = runBlocking {
        first.blockedBy = OPEN

        val prepared = router.prepareNewsSummary()
        val result = prepared.summarize(KEYWORD, ARTICLES)

        assertEquals(first.model.provider, prepared.plan.provider)
        assertEquals(second.name, result.metadata.model)
    }

    @Test
    @DisplayName("plan의 후보가 실패하면 다음 후보가 요약한다")
    fun summarizeWithNextCandidateWhenPlanCandidateFails() = runBlocking {
        val prepared = router.prepareNewsSummary()
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)

        val result = prepared.summarize(KEYWORD, ARTICLES)

        assertEquals(second.name, result.metadata.model)
        assertEquals(first.model.provider, prepared.plan.provider)
    }

    @Test
    @DisplayName("모든 후보가 차단돼도 요약 준비는 plan을 돌려준다")
    fun prepareReturnsPlanWhenAllCandidatesAreBlocked() = runBlocking {
        listOf(first, second).forEach { it.blockedBy = OPEN }

        val prepared = router.prepareNewsSummary()
        val failure = assertFailsWith<LlmProviderException> { prepared.summarize(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, failure.failure.code)
        assertNull(failure.failure.provider)
    }

    @Test
    @DisplayName("차단이 풀린 후보는 다시 후보가 된다")
    fun retryCandidateOnceUnblocked() = runBlocking {
        first.blockedBy = OPEN
        router.summarizeNews(KEYWORD, ARTICLES)
        assertEquals(0, first.summarizeCallCount)

        first.blockedBy = null
        router.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(1, first.summarizeCallCount)
    }

    @Test
    @DisplayName("키워드 확장도 같은 failover를 따른다")
    fun expandKeywordFollowsFailover() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)

        val result = router.expandKeyword(KEYWORD, 3)

        assertEquals(1, first.expandCallCount)
        assertEquals(second.name, result.metadata.model)
    }

    private fun candidate(model: LlmModel): FakeLlmProviderCandidate {
        return FakeLlmProviderCandidate(model).also { it.callLog = callLog }
    }

    private fun router(
        summary: List<FakeLlmProviderCandidate>,
        expansion: List<FakeLlmProviderCandidate>
    ): RoutingLlmProvider {
        return RoutingLlmProvider(
            candidates = mapOf(LlmUse.NEWS_SUMMARY to summary, LlmUse.KEYWORD_EXPANSION to expansion),
            clock = clock
        )
    }

    private companion object {
        const val OPEN = "open"
        const val COOLDOWN = "cooldown"
        val KEYWORD: AiKeyword = AiTestFixture.keyword()
        val ARTICLES = listOf(AiTestFixture.newsArticle())
    }
}
