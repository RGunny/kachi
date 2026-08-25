package me.rgunny.kachi.ai.application.port.inbound.outbox

import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RecoverAiOutboxCommand
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RecoverAiOutboxResult

/**
 * 운영자가 원인을 확인한 DEAD 행을 다시 발행 대상으로 되돌린다.
 */
interface RecoverAiOutboxUseCase {

    suspend fun recover(command: RecoverAiOutboxCommand): RecoverAiOutboxResult
}
