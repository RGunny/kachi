package me.rgunny.kachi.story.adapter.inbound.web

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.story.domain.Story

/**
 * story 한 건의 조회 응답.
 *
 * centroid는 싣지 않는다.
 */
data class StoryResponse(
    val id: UUID,
    val status: String,
    val articleCount: Int,
    val keywords: List<String>,
    val openedAt: Instant,
    val lastArticleAt: Instant,
    val closedAt: Instant?,
    val parentStoryId: UUID?,
    val mergedInto: UUID?,
    val version: Long
) {
    companion object {

        fun from(story: Story): StoryResponse {
            return StoryResponse(
                id = story.id.value,
                status = story.status.name,
                articleCount = story.articleCount,
                keywords = story.keywords.map { it.value }.sorted(),
                openedAt = story.openedAt,
                lastArticleAt = story.lastArticleAt,
                closedAt = story.closedAt,
                parentStoryId = story.parentStoryId?.value,
                mergedInto = story.mergedInto?.value,
                version = story.version
            )
        }
    }
}
