package me.rgunny.kachi.story.application.port.inbound.story.model

import me.rgunny.kachi.story.domain.Story

/**
 * story 목록 조회의 결과.
 */
data class FindStoriesResult(
    val stories: List<Story>
)
