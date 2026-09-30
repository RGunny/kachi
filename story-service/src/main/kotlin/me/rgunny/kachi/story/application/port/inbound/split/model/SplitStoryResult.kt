package me.rgunny.kachi.story.application.port.inbound.split.model

import me.rgunny.kachi.story.domain.Story

/**
 * 분리의 결과.
 *
 * [original]은 남은 기사로, [newStory]는 옮긴 기사로 파생 상태를 다시 계산한 story다.
 */
data class SplitStoryResult(
    val original: Story,
    val newStory: Story,
    val movedArticleCount: Int
)
