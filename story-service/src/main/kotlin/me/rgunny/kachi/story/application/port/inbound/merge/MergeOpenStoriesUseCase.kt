package me.rgunny.kachi.story.application.port.inbound.merge

import me.rgunny.kachi.story.application.port.inbound.merge.model.MergeOpenStoriesResult

/**
 * 가까워진 OPEN story 쌍을 찾아 합치는 유스케이스.
 */
interface MergeOpenStoriesUseCase {

    suspend fun mergeOpenStories(): MergeOpenStoriesResult
}
