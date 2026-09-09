package me.rgunny.kachi.collector.application.port.inbound.outbox

import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RecoverCollectorOutboxCommand
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RecoverCollectorOutboxResult

/**
 * 운영자가 원인을 확인한 DEAD 행을 다시 발행 대상으로 되돌리는 유스케이스.
 */
interface RecoverCollectorOutboxUseCase {

    suspend fun recover(command: RecoverCollectorOutboxCommand): RecoverCollectorOutboxResult
}
