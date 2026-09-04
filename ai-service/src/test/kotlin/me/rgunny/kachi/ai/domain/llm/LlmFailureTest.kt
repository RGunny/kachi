package me.rgunny.kachi.ai.domain.llm

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("LlmFailure")
class LlmFailureTest {

    @ParameterizedTest
    @EnumSource(value = LlmFailureCode::class, names = [
        "LLM_TIMEOUT",
        "LLM_RATE_LIMITED",
        "LLM_TRANSIENT_ERROR",
        "LLM_NETWORK_ERROR",
        "LLM_PROVIDER_UNAVAILABLE"
    ])
    @DisplayName("timeout·rate limit·일시 오류·provider 불능은 재시도 가능하다")
    fun retryableCodes(code: LlmFailureCode) {
        assertTrue(failure(code).retryable)
    }

    // 재시도 가능이 allowlist이므로 나머지 전부가 대상이다. 코드가 늘면 이 테스트가 먼저 판정을 요구한다.
    // 위 목록과 같은 값을 쓴다. 한쪽만 고치면 새 코드가 이 테스트로 넘어와 바로 실패한다.
    @ParameterizedTest
    @EnumSource(value = LlmFailureCode::class, mode = EnumSource.Mode.EXCLUDE, names = [
        "LLM_TIMEOUT",
        "LLM_RATE_LIMITED",
        "LLM_TRANSIENT_ERROR",
        "LLM_NETWORK_ERROR",
        "LLM_PROVIDER_UNAVAILABLE"
    ])
    @DisplayName("재시도 가능 목록 밖의 실패는 재시도 대상이 아니다")
    fun nonRetryableCodes(code: LlmFailureCode) {
        assertFalse(failure(code).retryable)
    }

    @Test
    @DisplayName("차단이 만든 실패만 실제 호출에서 나오지 않은 것으로 본다")
    fun fromActualCallCodes() {
        val notFromCall = LlmFailureCode.entries.filterNot { failure(it).fromActualCall }

        assertEquals(listOf(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE), notFromCall)
    }

    @Test
    @DisplayName("응답 계약 위반과 요청 검증 실패만 키워드에 귀속된다")
    fun keywordBoundCodes() {
        val keywordBound = LlmFailureCode.entries.filter { failure(it).keywordBound }

        assertEquals(
            listOf(LlmFailureCode.LLM_CLIENT_ERROR, LlmFailureCode.LLM_INVALID_RESPONSE),
            keywordBound
        )
    }

    @ParameterizedTest
    @EnumSource(LlmFailureCode::class)
    @DisplayName("원천과 분류는 코드에서 파생된다")
    fun sourceAndCategoryAreDerivedFromCode(code: LlmFailureCode) {
        val failure = failure(code)

        assertEquals(code.source, failure.source)
        assertEquals(code.category, failure.category)
    }

    @Test
    @DisplayName("provider 불능은 application 원천의 UNAVAILABLE 분류다")
    fun providerUnavailableClassification() {
        val failure = failure(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE)

        assertEquals(LlmFailureSource.APPLICATION, failure.source)
        assertEquals(LlmFailureCategory.UNAVAILABLE, failure.category)
        // 다음 tick에는 풀릴 수 있지만 provider를 호출해서 얻은 실패가 아니다.
        assertTrue(failure.retryable)
        assertFalse(failure.fromActualCall)
        assertFalse(failure.keywordBound)
    }

    @Test
    @DisplayName("호출이 나가지 않은 실패의 provider code는 none이다")
    fun providerCodeOfUncalledFailure() {
        val failure = LlmFailure(code = LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, provider = null)

        assertEquals(LlmFailure.NO_PROVIDER, failure.providerCode)
        assertEquals(PROVIDER.code, failure(LlmFailureCode.LLM_TIMEOUT).providerCode)
    }

    @Test
    @DisplayName("빈 메시지로 만들 수 없다")
    fun rejectBlankMessage() {
        assertFailsWith<IllegalArgumentException> {
            LlmFailure(
                code = LlmFailureCode.LLM_TIMEOUT,
                provider = PROVIDER,
                message = " "
            )
        }
    }

    @Test
    @DisplayName("음수 Retry-After로 만들 수 없다")
    fun rejectNegativeRetryAfter() {
        assertFailsWith<IllegalArgumentException> {
            LlmFailure(
                code = LlmFailureCode.LLM_RATE_LIMITED,
                provider = PROVIDER,
                retryAfterMillis = -1
            )
        }
    }

    @Test
    @DisplayName("메시지 기본값은 코드의 기본 메시지다")
    fun defaultMessageComesFromCode() {
        val failure = LlmFailure(code = LlmFailureCode.LLM_TIMEOUT, provider = PROVIDER)

        assertEquals(LlmFailureCode.LLM_TIMEOUT.defaultMessage, failure.message)
    }

    private fun failure(code: LlmFailureCode): LlmFailure {
        return LlmFailure(code = code, provider = PROVIDER)
    }

    private companion object {
        val PROVIDER: LlmProvider = LlmProvider.GROQ
    }
}
