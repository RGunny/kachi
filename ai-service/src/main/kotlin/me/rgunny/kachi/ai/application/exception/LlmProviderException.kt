package me.rgunny.kachi.ai.application.exception

import me.rgunny.kachi.ai.domain.llm.LlmFailure
import me.rgunny.kachi.ai.domain.llm.LlmFailureAttribution

/**
 * LLM 호출 실패를 분류 결과와 함께 전달하는 예외.
 *
 * [failure]는 대표 실패다. 후보 여럿을 거쳤으면 마지막 실제 호출의 실패이고, [attempts]에 실제 호출로 끝난 실패가 순서대로 있다.
 * 차단은 호출이 아니므로 [attempts]에 들어가지 않는다.
 * 소비처는 예외 타입이 아니라 이 값들로 실패 원인 기록, 격리 카운트, 조기 중단을 판단한다.
 */
class LlmProviderException(
    val failure: LlmFailure,
    cause: Throwable? = null,
    val attempts: List<LlmFailure> = listOf(failure)
) : AiException(
    errorCode = AiCommonErrorCode.LLM_PROVIDER_CALL_FAILED,
    message = "${failure.code.code} ${failure.message} (provider=${failure.providerCode})",
    cause = cause
) {
    init {
        require(attempts.isNotEmpty()) { "LLM 실패 예외는 시도 기록이 하나 이상이어야 합니다" }
    }

    /**
     * 시도한 후보 전부가 이 키워드의 입력 탓으로 끝났지.
     *
     * 한 모델의 거부는 그 모델 탓일 수 있으므로 후보 하나로는 판단하지 않는다. 시도한 모든 후보가 같은 판정을 냈을 때만 true
     * 실제 호출이 없었으면 false.
     */
    val allInput: Boolean
        get() = attempts.all { it.attribution == LlmFailureAttribution.INPUT }
}
