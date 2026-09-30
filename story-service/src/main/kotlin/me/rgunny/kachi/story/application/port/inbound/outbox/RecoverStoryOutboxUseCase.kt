package me.rgunny.kachi.story.application.port.inbound.outbox

import me.rgunny.kachi.story.application.port.inbound.outbox.model.RecoverStoryOutboxCommand
import me.rgunny.kachi.story.application.port.inbound.outbox.model.RecoverStoryOutboxResult

/**
 * 운영자가 원인을 확인한 DEAD 행을 다시 발행 대상으로 되돌리는 입력 포트.
 */
interface RecoverStoryOutboxUseCase {

    suspend fun recover(command: RecoverStoryOutboxCommand): RecoverStoryOutboxResult
}
