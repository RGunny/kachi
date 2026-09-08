package me.rgunny.kachi.collector.adapter.inbound.outbox

import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RelayCollectorOutboxResult

/**
 * 요청이 실행되어 tick 한 번이 끝난 결과.
 */
data class CollectorOutboxRelayFinished(
    val result: RelayCollectorOutboxResult
) : CollectorOutboxRelayExecutionResult
