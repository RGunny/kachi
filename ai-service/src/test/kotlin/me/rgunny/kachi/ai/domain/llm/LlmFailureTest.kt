package me.rgunny.kachi.ai.domain.llm

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("LlmFailure")
class LlmFailureTest {

    @Test
    @DisplayName("timeout·rate limit·일시 오류·provider 불능은 재시도 가능하다")
    fun retryableCodes() {
        val codes = listOf(
            LlmFailureCode.LLM_TIMEOUT,
            LlmFailureCode.LLM_RATE_LIMITED,
            LlmFailureCode.LLM_TRANSIENT_ERROR,
            LlmFailureCode.LLM_NETWORK_ERROR,
            LlmFailureCode.LLM_PROVIDER_UNAVAILABLE
        )

        codes.forEach { code ->
            assertTrue(failure(code).retryable, "$code must be retryable")
        }
    }

    @Test
    @DisplayName("검증·인증·계약 위반·분류 불가 실패는 재시도 대상이 아니다")
    fun nonRetryableCodes() {
        val codes = listOf(
            LlmFailureCode.LLM_CLIENT_ERROR,
            LlmFailureCode.LLM_AUTHORIZATION_ERROR,
            LlmFailureCode.LLM_INVALID_RESPONSE,
            LlmFailureCode.LLM_UNKNOWN_ERROR
        )

        codes.forEach { code ->
            assertFalse(failure(code).retryable, "$code must not be retryable")
        }
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

    @Test
    @DisplayName("원천과 분류는 코드에서 파생된다")
    fun sourceAndCategoryAreDerivedFromCode() {
        LlmFailureCode.entries.forEach { code ->
            val failure = failure(code)

            assertEquals(code.source, failure.source, "$code source")
            assertEquals(code.category, failure.category, "$code category")
        }
    }

    @Test
    @DisplayName("provider 불능은 application 원천의 UNAVAILABLE 분류다")
    fun providerUnavailableClassification() {
        val failure = failure(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE)

        assertEquals(LlmFailureSource.APPLICATION, failure.source)
        assertEquals(LlmFailureCategory.UNAVAILABLE, failure.category)
        assertTrue(failure.retryable)
        assertFalse(failure.keywordBound)
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
        val PROVIDER: LlmProviderName = LlmProviderName.of("groq")
    }
}
