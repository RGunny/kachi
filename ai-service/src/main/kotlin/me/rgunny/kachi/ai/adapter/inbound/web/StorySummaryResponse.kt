package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.inbound.story.model.StorySummarySnapshot
import java.time.Instant
import java.util.UUID

/**
 * story 요약 버전 조회 응답.
 */
data class StorySummaryResponse(
    val id: UUID,
    val storyId: UUID,
    val version: Long,
    val keywords: List<String>,
    val newNewsIds: List<UUID>,
    val sourceNewsCount: Int,
    val title: String,
    val content: String,
    val sentiment: String,
    val developmentKind: String,
    val provider: String,
    val model: String,
    val promptVersion: String,
    val createdAt: Instant
) {
    companion object {

        fun from(snapshot: StorySummarySnapshot): StorySummaryResponse {
            return StorySummaryResponse(
                id = snapshot.id.value,
                storyId = snapshot.storyId.value,
                version = snapshot.version,
                keywords = snapshot.keywords,
                newNewsIds = snapshot.newNewsIds,
                sourceNewsCount = snapshot.sourceNewsCount,
                title = snapshot.title,
                content = snapshot.content,
                sentiment = snapshot.sentiment.name,
                developmentKind = snapshot.developmentKind.name,
                provider = snapshot.provider,
                model = snapshot.model,
                promptVersion = snapshot.promptVersion,
                createdAt = snapshot.createdAt
            )
        }
    }
}
