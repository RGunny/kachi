package me.rgunny.kachi.story.application.port.inbound.story.model

import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryArticle

/**
 * story 하나와 그 구성 기사 전체.
 *
 * 기사는 붙은 순이다.
 */
data class StoryDetail(
    val story: Story,
    val articles: List<StoryArticle>
)
