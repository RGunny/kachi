package me.rgunny.kachi.story.adapter.inbound.web

import java.util.UUID
import me.rgunny.kachi.story.application.port.inbound.merge.model.MergeStoryPairResult

/**
 * 운영자 병합 응답.
 */
data class MergeStoryPairResponse(
    val survivorStoryId: UUID,
    val mergedStoryId: UUID,
    val articleCount: Int
) {
    companion object {

        fun from(result: MergeStoryPairResult): MergeStoryPairResponse {
            return MergeStoryPairResponse(
                survivorStoryId = result.survivor.id.value,
                mergedStoryId = result.mergedStoryId.value,
                articleCount = result.survivor.articleCount
            )
        }
    }
}
