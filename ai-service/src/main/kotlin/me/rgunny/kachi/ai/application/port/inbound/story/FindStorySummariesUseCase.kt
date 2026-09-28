package me.rgunny.kachi.ai.application.port.inbound.story

import me.rgunny.kachi.ai.application.port.inbound.story.model.FindStorySummariesQuery
import me.rgunny.kachi.ai.application.port.inbound.story.model.FindStorySummariesResult

/**
 * 운영자가 story의 요약 버전들을 조회하는 유스케이스.
 */
interface FindStorySummariesUseCase {

    suspend fun find(query: FindStorySummariesQuery): FindStorySummariesResult
}
