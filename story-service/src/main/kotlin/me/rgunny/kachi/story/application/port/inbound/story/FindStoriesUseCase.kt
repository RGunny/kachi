package me.rgunny.kachi.story.application.port.inbound.story

import me.rgunny.kachi.story.application.port.inbound.story.model.FindStoriesQuery
import me.rgunny.kachi.story.application.port.inbound.story.model.FindStoriesResult

/**
 * 상태와 시작 시각으로 story 목록을 읽는 유스케이스.
 */
interface FindStoriesUseCase {

    suspend fun find(query: FindStoriesQuery): FindStoriesResult
}
