package me.rgunny.kachi.story.application.port.inbound.split.model

import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryId

/**
 * 분리 명령.
 *
 * [newsIds]의 기사가 [storyId]에서 새 story로 옮겨진다.
 */
data class SplitStoryCommand(
    val storyId: StoryId,
    val newsIds: List<NewsId>
)
