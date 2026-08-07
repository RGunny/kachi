package me.rgunny.kachi.ai.application.exception

import me.rgunny.kachi.ai.domain.llm.LlmFailure

/**
 * LLM provider 호출 실패를 분류 결과와 함께 전달하는 예외.
 */
class LlmProviderException(
    val failure: LlmFailure,
    cause: Throwable? = null
) : RuntimeException(
    "${failure.code} ${failure.message} (provider=${failure.provider.value})",
    cause
)
