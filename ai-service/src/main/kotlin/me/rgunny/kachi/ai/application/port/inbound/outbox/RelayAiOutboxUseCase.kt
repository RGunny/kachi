package me.rgunny.kachi.ai.application.port.inbound.outbox

import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RelayAiOutboxResult

/**
 * 기록된 outbox 이벤트를 발행 상태로 진행시키는 입력 포트.
 */
interface RelayAiOutboxUseCase {

    suspend fun relay(): RelayAiOutboxResult
}
