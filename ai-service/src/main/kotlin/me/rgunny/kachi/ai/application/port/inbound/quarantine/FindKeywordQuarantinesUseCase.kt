package me.rgunny.kachi.ai.application.port.inbound.quarantine

import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindKeywordQuarantinesQuery
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindKeywordQuarantinesResult

/**
 * 키워드 격리 기록을 조회하는 유스케이스.
 */
interface FindKeywordQuarantinesUseCase {

    suspend fun find(query: FindKeywordQuarantinesQuery): FindKeywordQuarantinesResult
}
