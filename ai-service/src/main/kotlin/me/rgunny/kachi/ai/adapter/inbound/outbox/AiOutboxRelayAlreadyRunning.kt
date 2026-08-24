package me.rgunny.kachi.ai.adapter.inbound.outbox

/**
 * 이미 실행 중인 tick이 있어 요청이 막힌 결과.
 */
data class AiOutboxRelayAlreadyRunning(
    val runningRelay: RunningAiOutboxRelay
) : AiOutboxRelayExecutionResult
