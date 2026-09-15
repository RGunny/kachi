package me.rgunny.kachi.story.application.port.inbound.story

import me.rgunny.kachi.story.application.port.inbound.story.model.StoryDetail
import me.rgunny.kachi.story.domain.StoryId

/**
 * story 하나와 그 구성 기사·판정 기록을 읽는 유스케이스.
 */
interface GetStoryUseCase {

    suspend fun get(storyId: StoryId): StoryDetail
}
