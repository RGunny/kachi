package me.rgunny.kachi.ai.adapter.outbound.persistence.story

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.StoryId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * story 상태 사본의 MongoDB Document.
 *
 * _id는 storyId이며 story 하나당 문서 하나다.
 * pending index는 tick의 요약 대상 조회(미요약 있음 + 기준 시각 경과)를 받친다.
 */
@Document(collection = "stories")
@CompoundIndexes(
    CompoundIndex(
        name = "ix_stories_pending_latest_version_at",
        def = "{'pendingCount': 1, 'latestVersionAt': 1}"
    ),
    CompoundIndex(
        name = "ix_stories_pending_oldest_pending_at",
        def = "{'pendingCount': 1, 'oldestPendingAt': 1}"
    )
)
data class AiStoryMongoDocument(
    @Id
    val id: UUID,
    val keywords: List<String>,
    val articleCount: Int,
    val latestVersion: Long,
    val latestVersionAt: Instant?,
    val pendingCount: Int,
    val oldestPendingAt: Instant?,
    val mergedInto: UUID?,
    val version: Long,
    val updatedAt: Instant
) {

    fun toDomain(): AiStory {
        return AiStory.restore(
            storyId = StoryId.of(id),
            keywords = keywords.map(AiKeyword::of),
            articleCount = articleCount,
            latestVersion = latestVersion,
            latestVersionAt = latestVersionAt,
            pendingCount = pendingCount,
            oldestPendingAt = oldestPendingAt,
            mergedInto = mergedInto?.let(StoryId::of),
            version = version,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(story: AiStory): AiStoryMongoDocument {
            return AiStoryMongoDocument(
                id = story.storyId.value,
                keywords = story.keywords.map { it.value },
                articleCount = story.articleCount,
                latestVersion = story.latestVersion,
                latestVersionAt = story.latestVersionAt,
                pendingCount = story.pendingCount,
                oldestPendingAt = story.oldestPendingAt,
                mergedInto = story.mergedInto?.value,
                version = story.version,
                updatedAt = story.updatedAt
            )
        }
    }
}
