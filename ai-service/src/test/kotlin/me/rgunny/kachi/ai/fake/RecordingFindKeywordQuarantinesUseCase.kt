package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.inbound.quarantine.FindKeywordQuarantinesUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindKeywordQuarantinesQuery
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindKeywordQuarantinesResult
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.KeywordQuarantineSummary

/**
 * 전달받은 조회 조건을 그대로 보관하는 격리 조회 유스케이스 fake.
 *
 * 컨트롤러가 요청 파라미터를 조회 조건으로 옳게 옮겼는지 확인하는 데 쓴다.
 */
class RecordingFindKeywordQuarantinesUseCase : FindKeywordQuarantinesUseCase {
    var invokeCount = 0
    var lastQuery: FindKeywordQuarantinesQuery? = null
    var quarantines: List<KeywordQuarantineSummary> = emptyList()

    override suspend fun find(query: FindKeywordQuarantinesQuery): FindKeywordQuarantinesResult {
        invokeCount += 1
        lastQuery = query

        return FindKeywordQuarantinesResult(quarantines)
    }
}
