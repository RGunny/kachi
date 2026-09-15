package me.rgunny.kachi.story.application.port.inbound.outbox

import me.rgunny.kachi.story.application.port.inbound.outbox.model.RelayStoryOutboxResult

/**
 * 기록된 outbox 이벤트를 발행 상태로 진행시키는 입력 포트.
 */
interface RelayStoryOutboxUseCase {

    suspend fun relay(): RelayStoryOutboxResult
}
