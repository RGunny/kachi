package me.rgunny.kachi.ai.application.exception

import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("LlmProviderException")
class LlmProviderExceptionTest {

    @Test
    @DisplayName("시도 기록을 주지 않으면 대표 실패 하나가 시도 기록이다")
    fun attemptsDefaultToTheFailure() {
        val exception = AiTestFixture.llmProviderException(LlmFailureCode.LLM_INVALID_RESPONSE)

        assertEquals(listOf(exception.failure), exception.attempts)
        assertTrue(exception.allInput)
    }

    @Test
    @DisplayName("시도한 후보 전부가 입력 탓이면 키워드 탓으로 확정한다")
    fun allInputWhenEveryAttemptIsInput() {
        val exception = AiTestFixture.llmProviderException(
            LlmFailureCode.LLM_REQUEST_REJECTED,
            LlmFailureCode.LLM_INVALID_RESPONSE
        )

        assertTrue(exception.allInput)
        assertEquals(LlmFailureCode.LLM_INVALID_RESPONSE, exception.failure.code)
    }

    @Test
    @DisplayName("한 후보라도 입력 탓이 아니면 키워드 탓으로 확정하지 않는다")
    fun notAllInputWhenAnyAttemptIsNotInput() {
        val exception = AiTestFixture.llmProviderException(
            LlmFailureCode.LLM_INVALID_RESPONSE,
            LlmFailureCode.LLM_TIMEOUT
        )

        assertFalse(exception.allInput)
    }

    @Test
    @DisplayName("실제 호출이 없었던 차단 실패는 입력 탓이 아니다")
    fun notAllInputWhenBlocked() {
        assertFalse(AiTestFixture.llmProviderException(LlmFailureCode.LLM_NOT_PERMITTED).allInput)
    }

    @Test
    @DisplayName("시도 기록이 비어 있으면 만들 수 없다")
    fun rejectEmptyAttempts() {
        assertFailsWith<IllegalArgumentException> {
            LlmProviderException(failure = AiTestFixture.llmFailure(LlmFailureCode.LLM_TIMEOUT), attempts = emptyList())
        }
    }
}
