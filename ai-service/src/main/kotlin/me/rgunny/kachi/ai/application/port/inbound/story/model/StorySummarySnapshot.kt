package me.rgunny.kachi.ai.application.port.inbound.story.model

import me.rgunny.kachi.ai.domain.story.StoryId
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.domain.summary.StoryDevelopmentKind
import me.rgunny.kachi.ai.domain.summary.StorySummary
import me.rgunny.kachi.ai.domain.summary.StorySummaryId
import java.time.Instant
import java.util.UUID

/**
 * 조회 응답이 쓰는 story 요약 버전 스냅샷.
 */
data class StorySummarySnapshot(
    val id: StorySummaryId,
    val storyId: StoryId,
    val version: Long,
    val keywords: List<String>,
    val newNewsIds: List<UUID>,
    val sourceNewsCount: Int,
    val title: String,
    val content: String,
    val sentiment: NewsSummarySentiment,
    val developmentKind: StoryDevelopmentKind,
    val provider: String,
    val model: String,
    val promptVersion: String,
    val createdAt: Instant
) {
    companion object {

        fun from(summary: StorySummary): StorySummarySnapshot {
            return StorySummarySnapshot(
                id = summary.id,
                storyId = summary.storyId,
                version = summary.version,
                keywords = summary.keywords.map { it.value },
                newNewsIds = summary.newNewsIds,
                sourceNewsCount = summary.sourceNewsCount,
                title = summary.title,
                content = summary.content,
                sentiment = summary.sentiment,
                developmentKind = summary.developmentKind,
                provider = summary.provider.code,
                model = summary.model,
                promptVersion = summary.promptVersion.value,
                createdAt = summary.createdAt
            )
        }
    }
}
