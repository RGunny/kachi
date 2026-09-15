package me.rgunny.kachi.story.adapter.inbound.web

import java.util.UUID
import me.rgunny.kachi.story.application.port.inbound.split.model.SplitStoryResult

/**
 * 분리 응답.
 */
data class SplitStoryResponse(
    val storyId: UUID,
    val newStoryId: UUID,
    val movedArticleCount: Int,
    val remainingArticleCount: Int
) {
    companion object {

        fun from(result: SplitStoryResult): SplitStoryResponse {
            return SplitStoryResponse(
                storyId = result.original.id.value,
                newStoryId = result.newStory.id.value,
                movedArticleCount = result.movedArticleCount,
                remainingArticleCount = result.original.articleCount
            )
        }
    }
}
