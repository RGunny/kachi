package me.rgunny.kachi.ai.application.port.inbound.quarantine

import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindStoryQuarantinesQuery
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindStoryQuarantinesResult

/**
 * 운영자가 story 격리 기록을 조회하는 유스케이스.
 */
interface FindStoryQuarantinesUseCase {

    suspend fun find(query: FindStoryQuarantinesQuery): FindStoryQuarantinesResult
}
