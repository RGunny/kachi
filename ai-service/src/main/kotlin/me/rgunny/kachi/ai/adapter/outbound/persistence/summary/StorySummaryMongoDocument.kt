package me.rgunny.kachi.ai.adapter.outbound.persistence.summary

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.story.StoryId
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.domain.summary.StoryDevelopmentKind
import me.rgunny.kachi.ai.domain.summary.StorySummary
import me.rgunny.kachi.ai.domain.summary.StorySummaryId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * story 요약 버전의 MongoDB Document.
 *
 * (storyId, version) unique index가 버전 하나당 문서 하나 계약을 보장한다.
 */
@Document(collection = "story_summaries")
@CompoundIndexes(
    CompoundIndex(
        name = "ux_story_summaries_story_version",
        def = "{'storyId': 1, 'version': -1}",
        unique = true
    )
)
data class StorySummaryMongoDocument(
    @Id
    val id: UUID,
    val storyId: UUID,
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
    val requestedModel: String,
    val promptVersion: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val createdAt: Instant
) {

    fun toDomain(): StorySummary {
        return StorySummary.restore(
            id = StorySummaryId.of(id),
            storyId = StoryId.of(storyId),
            version = version,
            keywords = keywords.map(AiKeyword::of),
            newNewsIds = newNewsIds,
            sourceNewsCount = sourceNewsCount,
            title = title,
            content = content,
            sentiment = sentiment,
            developmentKind = developmentKind,
            provider = LlmProvider.ofCode(provider),
            model = model,
            requestedModel = requestedModel,
            promptVersion = PromptVersion.of(promptVersion),
            tokenUsage = TokenUsage(inputTokens = inputTokens, outputTokens = outputTokens),
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(summary: StorySummary): StorySummaryMongoDocument {
            return StorySummaryMongoDocument(
                id = summary.id.value,
                storyId = summary.storyId.value,
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
                requestedModel = summary.requestedModel,
                promptVersion = summary.promptVersion.value,
                inputTokens = summary.tokenUsage.inputTokens,
                outputTokens = summary.tokenUsage.outputTokens,
                createdAt = summary.createdAt
            )
        }
    }
}
