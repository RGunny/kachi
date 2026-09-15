package me.rgunny.kachi.story.application.port.inbound.outbox

import me.rgunny.kachi.story.application.port.inbound.outbox.model.FindStoryOutboxesQuery
import me.rgunny.kachi.story.application.port.inbound.outbox.model.FindStoryOutboxesResult

/**
 * 발행 상태별 outbox 행을 조회하는 입력 포트.
 */
interface FindStoryOutboxesUseCase {

    suspend fun find(query: FindStoryOutboxesQuery): FindStoryOutboxesResult
}
