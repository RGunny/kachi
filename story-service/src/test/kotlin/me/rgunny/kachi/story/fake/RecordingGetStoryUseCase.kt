package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.inbound.story.GetStoryUseCase
import me.rgunny.kachi.story.application.port.inbound.story.model.StoryDetail
import me.rgunny.kachi.story.domain.StoryId

/**
 * 요청한 id를 기록하고 정해 둔 상세나 실패를 돌려주는 story 상세 유스케이스.
 */
class RecordingGetStoryUseCase : GetStoryUseCase {
    var detail: StoryDetail? = null
    var failure: Throwable? = null
    var lastStoryId: StoryId? = null

    override suspend fun get(storyId: StoryId): StoryDetail {
        lastStoryId = storyId
        failure?.let { throw it }

        return requireNotNull(detail) { "돌려줄 상세를 지정하지 않았습니다" }
    }
}
