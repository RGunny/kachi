package me.rgunny.kachi.ai.application.exception

import me.rgunny.kachi.ai.domain.llm.LlmFailure

/**
 * LLM provider 호출 실패를 분류 결과와 함께 전달하는 예외.
 *
 * 소비처는 예외 타입이 아니라 [failure]의 값으로 실패 원인 기록, 격리 카운트, 조기 중단을 판단한다(ADR 021).
 */
class LlmProviderException(
    val failure: LlmFailure,
    cause: Throwable? = null
) : AiException(
    errorCode = AiCommonErrorCode.LLM_PROVIDER_CALL_FAILED,
    message = "${failure.code.code} ${failure.message} (provider=${failure.provider.value})",
    cause = cause
)
