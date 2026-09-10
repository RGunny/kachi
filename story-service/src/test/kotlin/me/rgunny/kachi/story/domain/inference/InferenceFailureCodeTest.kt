package me.rgunny.kachi.story.domain.inference

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("InferenceFailureCode")
class InferenceFailureCodeTest {

    @ParameterizedTest
    @CsvSource(
        "INFERENCE_INPUT_EMPTY, INPUT, false",
        "INFERENCE_PAYLOAD_TOO_LARGE, INPUT, false",
        "INFERENCE_INPUT_INVALID, INPUT, false",
        "INFERENCE_FAILED, SERVER, true",
        "INFERENCE_OVERLOADED, SERVER, true",
        "INFERENCE_UNHEALTHY, SERVER, true",
        "INFERENCE_SERVER_ERROR, SERVER, true",
        "INFERENCE_TIMEOUT, SERVER, true",
        "INFERENCE_NETWORK_ERROR, SERVER, true",
        "INFERENCE_INVALID_RESPONSE, MODEL, false",
        "INFERENCE_NOT_PERMITTED, NONE, true",
        "INFERENCE_UNKNOWN_ERROR, SERVER, true"
    )
    @DisplayName("상수마다 책임·지속 두 축이 표대로다")
    fun axesPerCode(code: InferenceFailureCode, attribution: InferenceFailureAttribution, transient: Boolean) {
        assertEquals(attribution, code.attribution)
        assertEquals(transient, code.transient)
        assertEquals(code.name, code.code)
    }

    @ParameterizedTest
    @EnumSource(InferenceFailureCode::class)
    @DisplayName("서킷 기록 여부는 실제 호출에서 나온 일시 실패일 때만 참이다")
    fun recordsInCircuitDerivation(code: InferenceFailureCode) {
        val failure = InferenceFailure(code = code, target = InferenceTarget.EMBEDDING)

        assertEquals(code.attribution != InferenceFailureAttribution.NONE, failure.fromActualCall)
        assertEquals(failure.fromActualCall && code.transient, failure.recordsInCircuit)
    }

    @Test
    @DisplayName("차단 실패와 입력·모델 실패는 서킷에 기록되지 않는다")
    fun notRecorded() {
        assertFalse(InferenceFailure(InferenceFailureCode.INFERENCE_NOT_PERMITTED, InferenceTarget.JUDGE).recordsInCircuit)
        assertFalse(InferenceFailure(InferenceFailureCode.INFERENCE_INPUT_INVALID, InferenceTarget.JUDGE).recordsInCircuit)
        assertFalse(InferenceFailure(InferenceFailureCode.INFERENCE_INVALID_RESPONSE, InferenceTarget.JUDGE).recordsInCircuit)
        assertTrue(InferenceFailure(InferenceFailureCode.INFERENCE_TIMEOUT, InferenceTarget.JUDGE).recordsInCircuit)
    }

    @Test
    @DisplayName("빈 메시지는 거부한다")
    fun rejectBlankMessage() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            InferenceFailure(InferenceFailureCode.INFERENCE_TIMEOUT, InferenceTarget.EMBEDDING, message = " ")
        }
    }
}
