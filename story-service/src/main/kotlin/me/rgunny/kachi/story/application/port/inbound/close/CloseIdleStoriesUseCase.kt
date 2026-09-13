package me.rgunny.kachi.story.application.port.inbound.close

import me.rgunny.kachi.story.application.port.inbound.close.model.CloseIdleStoriesResult

/**
 * 오래 조용한 OPEN story를 닫고 그 벡터를 색인에서 빼는 유스케이스.
 */
interface CloseIdleStoriesUseCase {

    suspend fun closeIdleStories(): CloseIdleStoriesResult
}
