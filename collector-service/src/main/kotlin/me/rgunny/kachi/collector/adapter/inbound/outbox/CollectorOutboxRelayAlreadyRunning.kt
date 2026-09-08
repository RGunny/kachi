package me.rgunny.kachi.collector.adapter.inbound.outbox

/**
 * 이미 실행 중인 tick이 있어 요청이 막힌 결과.
 */
data class CollectorOutboxRelayAlreadyRunning(
    val runningRelay: RunningCollectorOutboxRelay
) : CollectorOutboxRelayExecutionResult
