package me.rgunny.kachi.ai.adapter.inbound.outbox

import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RelayAiOutboxResult

/**
 * 요청이 실행되어 tick 한 번이 끝난 결과.
 */
data class AiOutboxRelayFinished(
    val result: RelayAiOutboxResult
) : AiOutboxRelayExecutionResult
