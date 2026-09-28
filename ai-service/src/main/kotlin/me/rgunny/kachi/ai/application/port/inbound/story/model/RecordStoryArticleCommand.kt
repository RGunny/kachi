package me.rgunny.kachi.ai.application.port.inbound.story.model

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.story.StoryId
import java.time.Instant
import java.util.UUID

/**
 * story에 붙은 기사 하나를 사본으로 기록하라는 명령.
 *
 * [storyKeywords]와 [storyArticleCount]는 기사를 붙인 뒤 story의 스냅샷이다.
 */
data class RecordStoryArticleCommand(
    val storyId: StoryId,
    val newsId: UUID,
    val source: String,
    val title: String,
    val excerpt: String,
    val url: String,
    val publishedAt: Instant,
    val storyKeywords: List<AiKeyword>,
    val storyArticleCount: Int,
    val attachedAt: Instant
) {
    init {
        require(storyKeywords.isNotEmpty()) { "story 키워드는 하나 이상이어야 합니다" }
        require(storyArticleCount >= 1) { "story 기사 수는 1 이상이어야 합니다: $storyArticleCount" }
    }
}
