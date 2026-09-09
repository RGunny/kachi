package me.rgunny.kachi.ai.application.port.inbound.outbox

import me.rgunny.kachi.ai.application.port.inbound.outbox.model.FindAiOutboxesQuery
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.FindAiOutboxesResult

/**
 * 발행 상태별 outbox 행을 조회하는 유스케이스.
 */
interface FindAiOutboxesUseCase {

    suspend fun find(query: FindAiOutboxesQuery): FindAiOutboxesResult
}
