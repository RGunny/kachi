package me.rgunny.kachi.ai.domain.llm

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 소비처가 내리는 판단 프로퍼티가 코드의 두 축에서 맞게 파생되는지 본다. 목록은 allowlist와 EXCLUDE 쌍으로 두어 코드가 늘면 판정을 요구한다.
 */
@DisplayName("LlmFailure")
class LlmFailureTest {

    @Test
    @DisplayName("차단이 만든 실패만 실제 호출에서 나오지 않은 것으로 본다")
    fun fromActualCallCodes() {
        val notFromCall = LlmFailureCode.entries.filterNot { failure(it).fromActualCall }

        assertEquals(listOf(LlmFailureCode.LLM_NOT_PERMITTED), notFromCall)
    }

    @ParameterizedTest
    @EnumSource(value = LlmFailureCode::class, names = [
        "LLM_RATE_LIMITED",
        "LLM_SERVER_ERROR",
        "LLM_TIMEOUT",
        "LLM_NETWORK_ERROR",
        "LLM_UNKNOWN_ERROR"
    ])
    @DisplayName("실제 호출에서 나온 일시 실패는 서킷에 기록한다")
    fun recordsInCircuitCodes(code: LlmFailureCode) {
        assertTrue(failure(code).recordsInCircuit)
    }

    // 위 목록과 같은 값을 쓴다. 한쪽만 고치면 새 코드가 이 테스트로 넘어와 바로 실패한다.
    @ParameterizedTest
    @EnumSource(value = LlmFailureCode::class, mode = EnumSource.Mode.EXCLUDE, names = [
        "LLM_RATE_LIMITED",
        "LLM_SERVER_ERROR",
        "LLM_TIMEOUT",
        "LLM_NETWORK_ERROR",
        "LLM_UNKNOWN_ERROR"
    ])
    @DisplayName("입력 탓, 한 건 확정, 차단 실패는 서킷에 기록하지 않는다")
    fun notRecordedInCircuitCodes(code: LlmFailureCode) {
        assertFalse(failure(code).recordsInCircuit)
    }

    @Test
    @DisplayName("모델이 없다는 응답만 모델을 보류한다")
    fun holdsModelCodes() {
        val holdsModel = LlmFailureCode.entries.filter { failure(it).holdsModel }

        assertEquals(listOf(LlmFailureCode.LLM_MODEL_NOT_FOUND), holdsModel)
    }

    @Test
    @DisplayName("인증·결제·권한 실패만 제공자를 보류한다")
    fun holdsProviderCodes() {
        val holdsProvider = LlmFailureCode.entries.filter { failure(it).holdsProvider }

        assertEquals(
            listOf(LlmFailureCode.LLM_UNAUTHORIZED, LlmFailureCode.LLM_PAYMENT_REQUIRED, LlmFailureCode.LLM_FORBIDDEN),
            holdsProvider
        )
    }

    @ParameterizedTest
    @EnumSource(LlmFailureCode::class)
    @DisplayName("책임과 지속은 코드에서 파생된다")
    fun axesAreDerivedFromCode(code: LlmFailureCode) {
        val failure = failure(code)

        assertEquals(code.attribution, failure.attribution)
        assertEquals(code.transient, failure.transient)
    }

    @Test
    @DisplayName("차단 실패는 어느 쪽 책임도 아닌 일시 실패다")
    fun notPermittedClassification() {
        val failure = failure(LlmFailureCode.LLM_NOT_PERMITTED)

        assertEquals(LlmFailureAttribution.NONE, failure.attribution)
        // 다음 tick에는 풀릴 수 있지만 provider를 호출해서 얻은 실패가 아니다.
        assertTrue(failure.transient)
        assertFalse(failure.fromActualCall)
        assertFalse(failure.recordsInCircuit)
        assertFalse(failure.holdsModel)
        assertFalse(failure.holdsProvider)
    }

    @Test
    @DisplayName("호출이 나가지 않은 실패의 provider code는 none이다")
    fun providerCodeOfUncalledFailure() {
        val failure = LlmFailure(code = LlmFailureCode.LLM_NOT_PERMITTED, provider = null)

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
