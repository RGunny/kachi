package me.rgunny.kachi.ai.adapter.outbound.llm

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.config.LlmProviderMode
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.fake.FakeLlmProviderCandidate
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.MutableClock
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * 순회 정책만 검증한다. 후보가 왜 차단되는지는 후보 구현의 몫이라 가용성은 fake에 직접 지정한다.
 */
@DisplayName("RoutingLlmProvider")
class RoutingLlmProviderTest {
    private val clock = MutableClock()
    private val callLog = mutableListOf<String>()
    private val candidates = candidates("a", "b")
    private val router = router(candidates)
    private val order = callOrder(candidates)
    private val first = order[0]
    private val second = order[1]

    @Test
    @DisplayName("single-random mode는 등록된 provider 중 하나를 호출한다")
    fun callSingleRandomProvider() = runBlocking {
        val candidates = candidates("openrouter", "groq", "mistral")
        val router = router(candidates)

        val result = router.expandKeyword(keyword = KEYWORD, maxExpansions = 3)

        assertEquals(1, candidates.count { it.expandCallCount == 1 })
        assertEquals(result.metadata.provider, candidates.first { it.expandCallCount == 1 }.provider)
    }

    @Test
    @DisplayName("provider가 없으면 생성할 수 없다")
    fun failWhenProvidersAreEmpty() {
        assertFailsWith<IllegalArgumentException> {
            RoutingLlmProvider(providers = emptyList(), mode = LlmProviderMode.SINGLE_RANDOM, clock = clock)
        }
    }

    @Test
    @DisplayName("aggregate mode는 아직 호출하지 않는다")
    fun aggregateModeIsNotImplemented() = runBlocking {
        val router = router(candidates, mode = LlmProviderMode.AGGREGATE)

        assertFailsWith<UnsupportedOperationException> { router.expandKeyword(KEYWORD, 3) }
        assertFailsWith<UnsupportedOperationException> { router.summarizeNews(KEYWORD, ARTICLES) }
        assertFailsWith<UnsupportedOperationException> { router.prepareNewsSummary() }
        Unit
    }

    @Test
    @DisplayName("재시도 가능한 실패면 다음 provider를 호출한다")
    fun failoverOnRetryableFailure() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)

        val result = router.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(1, first.summarizeCallCount)
        assertEquals(1, second.summarizeCallCount)
        assertEquals(second.provider, result.metadata.provider)
    }

    @Test
    @DisplayName("키워드 귀속 실패는 다른 provider를 시도하지 않는다")
    fun doNotFailoverOnKeywordBoundFailure() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_INVALID_RESPONSE)

        val failure = assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_INVALID_RESPONSE, failure.failure.code)
        assertEquals(0, second.summarizeCallCount)
    }

    @Test
    @DisplayName("요청 검증 실패도 다른 provider를 시도하지 않는다")
    fun doNotFailoverOnValidationFailure() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_CLIENT_ERROR)

        val failure = assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_CLIENT_ERROR, failure.failure.code)
        assertEquals(0, second.summarizeCallCount)
    }

    @Test
    @DisplayName("인증 실패는 다른 provider로 넘어간다")
    fun failoverOnAuthorizationFailure() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_AUTHORIZATION_ERROR)

        val result = router.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(second.provider, result.metadata.provider)
    }

    @Test
    @DisplayName("분류되지 않은 LLM 실패는 다른 provider로 넘어간다")
    fun failoverOnUnknownFailure() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_UNKNOWN_ERROR)

        val result = router.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(second.provider, result.metadata.provider)
    }

    @Test
    @DisplayName("LLM 실패가 아닌 예외는 즉시 전파한다")
    fun propagateNonLlmException() = runBlocking {
        first.failures += IllegalArgumentException("boom")

        assertFailsWith<IllegalArgumentException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(0, second.summarizeCallCount)
    }

    @Test
    @DisplayName("각 provider는 한 호출에 최대 한 번만 시도한다")
    fun tryEachProviderOnce() = runBlocking {
        val candidates = candidates("a", "b", "c")
        val router = router(candidates)
        candidates.forEach { it.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT) }

        assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        candidates.forEach { assertEquals(1, it.summarizeCallCount, "${it.name} must be called once") }
        assertEquals(3, callLog.size)
    }

    @Test
    @DisplayName("차단된 provider는 호출 없이 건너뛴다")
    fun skipBlockedProviderWithoutCall() = runBlocking {
        first.blockedBy = OPEN

        val result = router.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(0, first.summarizeCallCount)
        assertEquals(1, second.summarizeCallCount)
        assertEquals(second.provider, result.metadata.provider)
    }

    @Test
    @DisplayName("모든 provider가 차단되면 호출 없이 실패한다")
    fun failWithoutCallWhenAllProvidersAreBlocked() = runBlocking {
        candidates.forEach { it.blockedBy = OPEN }

        val failure = assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, failure.failure.code)
        assertEquals(LlmProviderName.NONE, failure.failure.provider)
        assertEquals(0, candidates.sumOf { it.summarizeCallCount })
        assertEquals(
            "no llm provider available: ${first.name}=$OPEN, ${second.name}=$OPEN",
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
    @DisplayName("차단 사유가 provider마다 다르면 각각 열거한다")
    fun listBlockedReasonPerProvider() = runBlocking {
        first.blockedBy = OPEN
        second.blockedBy = COOLDOWN

        val failure = assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(
            "no llm provider available: ${first.name}=$OPEN, ${second.name}=$COOLDOWN",
            failure.failure.message
        )
    }

    @Test
    @DisplayName("요약은 plan의 provider를 먼저 호출한다")
    fun callPlanProviderFirst() = runBlocking {
        val prepared = router.prepareNewsSummary()
        prepared.summarize(KEYWORD, ARTICLES)

        assertEquals(prepared.plan.provider.value, callLog.first())
    }

    @Test
    @DisplayName("plan의 provider가 실패하면 다른 provider가 요약한다")
    fun summarizeWithNextProviderWhenPlanProviderFails() = runBlocking {
        val prepared = router.prepareNewsSummary()
        val planProvider = candidates.first { it.provider == prepared.plan.provider }
        val other = candidates.first { it.provider != prepared.plan.provider }
        planProvider.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)

        val result = prepared.summarize(KEYWORD, ARTICLES)

        assertEquals(other.provider, result.metadata.provider)
        assertEquals(planProvider.provider, prepared.plan.provider)
    }

    @Test
    @DisplayName("모든 provider가 차단돼도 요약 준비는 plan을 돌려준다")
    fun prepareReturnsPlanWhenAllProvidersAreBlocked() = runBlocking {
        candidates.forEach { it.blockedBy = OPEN }

        val prepared = router.prepareNewsSummary()
        val failure = assertFailsWith<LlmProviderException> { prepared.summarize(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, failure.failure.code)
        assertEquals(LlmProviderName.NONE, failure.failure.provider)
    }

    @Test
    @DisplayName("호출 가능해 보이는 provider를 plan으로 우선 고른다")
    fun preferAvailableProviderAsPlan() = runBlocking {
        repeat(10) { seed ->
            val candidates = candidates("a", "b")
            val router = router(candidates, seed = seed)
            candidates.first { it.name == "a" }.blockedBy = OPEN

            assertEquals("b", router.prepareNewsSummary().plan.provider.value, "seed=$seed")
        }
    }

    @Test
    @DisplayName("같은 seed면 같은 순서로 시도한다")
    fun sameSeedGivesSameOrder() = runBlocking {
        val firstLog = failoverCallLog(seed = 7)
        val secondLog = failoverCallLog(seed = 7)

        assertEquals(firstLog, secondLog)
        assertEquals(3, firstLog.size)
    }

    @Test
    @DisplayName("차단이 풀린 provider는 다시 후보가 된다")
    fun retryProviderOnceUnblocked() = runBlocking {
        first.blockedBy = OPEN
        router.summarizeNews(KEYWORD, ARTICLES)
        assertEquals(0, first.summarizeCallCount)

        first.blockedBy = null
        second.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)
        router.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(1, first.summarizeCallCount)
    }

    @Test
    @DisplayName("키워드 확장도 같은 failover를 따른다")
    fun expandKeywordFollowsFailover() = runBlocking {
        first.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)

        val result = router.expandKeyword(KEYWORD, 3)

        assertEquals(1, first.expandCallCount)
        assertEquals(second.provider, result.metadata.provider)
    }

    /**
     * 전 provider가 실패하는 동안의 호출 순서. 같은 seed가 같은 순서를 만드는지 확인하는 데 쓴다.
     */
    private suspend fun failoverCallLog(seed: Int): List<String> {
        val candidates = candidates("a", "b", "c")
        val router = router(candidates, seed = seed)
        candidates.forEach { it.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT) }

        assertFailsWith<LlmProviderException> { router.summarizeNews(KEYWORD, ARTICLES) }

        return callLog.toList().also { callLog.clear() }
    }

    /**
     * router가 만들 후보 순서. 전원이 호출 가능할 때 router와 같은 seed로 섞은 결과와 같다.
     */
    private fun callOrder(candidates: List<FakeLlmProviderCandidate>): List<FakeLlmProviderCandidate> {
        return candidates.shuffled(Random(SEED))
    }

    private fun candidates(vararg names: String): List<FakeLlmProviderCandidate> {
        return names.map { name -> FakeLlmProviderCandidate(name).also { it.callLog = callLog } }
    }

    private fun router(
        candidates: List<FakeLlmProviderCandidate>,
        mode: LlmProviderMode = LlmProviderMode.SINGLE_RANDOM,
        seed: Int = SEED
    ): RoutingLlmProvider {
        return RoutingLlmProvider(providers = candidates, mode = mode, clock = clock, random = Random(seed))
    }

    private companion object {
        const val SEED = 1
        const val OPEN = "open"
        const val COOLDOWN = "cooldown"
        val KEYWORD: AiKeyword = AiTestFixture.keyword()
        val ARTICLES = listOf(AiTestFixture.newsArticle())
    }
}
