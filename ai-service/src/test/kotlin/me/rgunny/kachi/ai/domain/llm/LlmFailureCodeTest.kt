package me.rgunny.kachi.ai.domain.llm

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals

/**
 * 코드마다 책임·지속 두 축이 결정 표(ADR 030)와 같은지 보는 테스트.
 */
@DisplayName("LlmFailureCode")
class LlmFailureCodeTest {

    @ParameterizedTest
    @EnumSource(LlmFailureCode::class)
    @DisplayName("코드마다 책임과 지속이 결정 표와 같다")
    fun attributionAndTransientFollowTheDecisionTable(code: LlmFailureCode) {
        val (attribution, transient) = expected(code)

        assertEquals(attribution, code.attribution, "${code.name} attribution")
        assertEquals(transient, code.transient, "${code.name} transient")
    }

    @ParameterizedTest
    @EnumSource(LlmFailureCode::class)
    @DisplayName("입력 탓 실패는 일시 실패가 아니고, 가드 차단은 항상 일시 실패다")
    fun inputIsNeverTransientAndNoneIsAlwaysTransient(code: LlmFailureCode) {
        when (code.attribution) {
            LlmFailureAttribution.INPUT -> assertEquals(false, code.transient, code.name)
            LlmFailureAttribution.NONE -> assertEquals(true, code.transient, code.name)
            LlmFailureAttribution.MODEL, LlmFailureAttribution.PROVIDER -> Unit
        }
    }

    private fun expected(code: LlmFailureCode): Pair<LlmFailureAttribution, Boolean> {
        return when (code) {
            LlmFailureCode.LLM_MODEL_NOT_FOUND -> LlmFailureAttribution.MODEL to false
            LlmFailureCode.LLM_REQUEST_REJECTED -> LlmFailureAttribution.INPUT to false
            LlmFailureCode.LLM_UNAUTHORIZED -> LlmFailureAttribution.PROVIDER to false
            LlmFailureCode.LLM_PAYMENT_REQUIRED -> LlmFailureAttribution.PROVIDER to false
            LlmFailureCode.LLM_FORBIDDEN -> LlmFailureAttribution.PROVIDER to false
            LlmFailureCode.LLM_RATE_LIMITED -> LlmFailureAttribution.PROVIDER to true
            LlmFailureCode.LLM_SERVER_ERROR -> LlmFailureAttribution.PROVIDER to true
            LlmFailureCode.LLM_TIMEOUT -> LlmFailureAttribution.PROVIDER to true
            LlmFailureCode.LLM_NETWORK_ERROR -> LlmFailureAttribution.PROVIDER to true
            LlmFailureCode.LLM_INVALID_RESPONSE -> LlmFailureAttribution.INPUT to false
            LlmFailureCode.LLM_NOT_PERMITTED -> LlmFailureAttribution.NONE to true
            LlmFailureCode.LLM_UNKNOWN_ERROR -> LlmFailureAttribution.PROVIDER to true
        }
    }
}
