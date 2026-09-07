package me.rgunny.kachi.ai.adapter.outbound.persistence

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import me.rgunny.kachi.ai.domain.summary.NewsSummaryId
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document(collection = "news_summaries")
@CompoundIndexes(
    CompoundIndex(
        name = "ux_news_summaries_keyword_news_hash_prompt",
        def = "{'keyword': 1, 'newsHash': 1, 'promptVersion': 1}",
        unique = true
    )
)
data class NewsSummaryMongoDocument(
    @Id
    val id: UUID,
    @Indexed
    val keyword: String,
    val sourceNewsIds: List<UUID>,
    val newsHash: String,
    val title: String,
    val content: String,
    val sentiment: NewsSummarySentiment,
    val provider: String,
    val model: String,
    val requestedModel: String,
    val promptVersion: String,
    val inputTokens: Int,
    val outputTokens: Int,
    @Indexed
    val createdAt: Instant
) {
    fun toDomain(): NewsSummary {
        return NewsSummary.restore(
            id = NewsSummaryId.of(id),
            keyword = AiKeyword.of(keyword),
            sourceNewsIds = sourceNewsIds,
            newsHash = newsHash,
            title = title,
            content = content,
            sentiment = sentiment,
            provider = LlmProvider.ofCode(provider),
            model = model,
            requestedModel = requestedModel,
            promptVersion = PromptVersion.of(promptVersion),
            tokenUsage = TokenUsage(inputTokens = inputTokens, outputTokens = outputTokens),
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(newsSummary: NewsSummary): NewsSummaryMongoDocument {
            return NewsSummaryMongoDocument(
                id = newsSummary.id.value,
                keyword = newsSummary.keyword.value,
                sourceNewsIds = newsSummary.sourceNewsIds,
                newsHash = newsSummary.newsHash,
                title = newsSummary.title,
                content = newsSummary.content,
                sentiment = newsSummary.sentiment,
                provider = newsSummary.provider.code,
                model = newsSummary.model,
                requestedModel = newsSummary.requestedModel,
                promptVersion = newsSummary.promptVersion.value,
                inputTokens = newsSummary.tokenUsage.inputTokens,
                outputTokens = newsSummary.tokenUsage.outputTokens,
                createdAt = newsSummary.createdAt
            )
        }
    }
}
