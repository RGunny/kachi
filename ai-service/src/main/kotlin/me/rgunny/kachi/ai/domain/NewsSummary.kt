package me.rgunny.kachi.ai.domain

import java.time.Instant
import java.util.UUID

class NewsSummary private constructor(
    val id: NewsSummaryId,
    val keyword: AiKeyword,
    val sourceNewsIds: List<UUID>,
    val sourceWindowHash: String,
    val title: String,
    val content: String,
    val sentiment: NewsSummarySentiment,
    val provider: LlmProviderName,
    val model: LlmModelName,
    val promptVersion: PromptVersion,
    val tokenUsage: TokenUsage,
    val createdAt: Instant
) {

    companion object {

        fun create(
            keyword: AiKeyword,
            sourceNewsIds: List<UUID>,
            sourceWindowHash: String,
            title: String,
            content: String,
            sentiment: NewsSummarySentiment,
            provider: LlmProviderName,
            model: LlmModelName,
            promptVersion: PromptVersion,
            tokenUsage: TokenUsage,
            createdAt: Instant
        ): NewsSummary {
            require(sourceNewsIds.isNotEmpty()) { "요약 대상 뉴스는 하나 이상이어야 합니다" }
            require(sourceWindowHash.isNotBlank()) { "source window hash는 빈 값일 수 없습니다" }
            require(title.isNotBlank()) { "요약 제목은 빈 값일 수 없습니다" }
            require(content.isNotBlank()) { "요약 본문은 빈 값일 수 없습니다" }

            return NewsSummary(
                id = NewsSummaryId.newId(),
                keyword = keyword,
                sourceNewsIds = sourceNewsIds.distinct(),
                sourceWindowHash = sourceWindowHash,
                title = title.trim(),
                content = content.trim(),
                sentiment = sentiment,
                provider = provider,
                model = model,
                promptVersion = promptVersion,
                tokenUsage = tokenUsage,
                createdAt = createdAt
            )
        }

        fun restore(
            id: NewsSummaryId,
            keyword: AiKeyword,
            sourceNewsIds: List<UUID>,
            sourceWindowHash: String,
            title: String,
            content: String,
            sentiment: NewsSummarySentiment,
            provider: LlmProviderName,
            model: LlmModelName,
            promptVersion: PromptVersion,
            tokenUsage: TokenUsage,
            createdAt: Instant
        ): NewsSummary {
            return NewsSummary(
                id = id,
                keyword = keyword,
                sourceNewsIds = sourceNewsIds,
                sourceWindowHash = sourceWindowHash,
                title = title,
                content = content,
                sentiment = sentiment,
                provider = provider,
                model = model,
                promptVersion = promptVersion,
                tokenUsage = tokenUsage,
                createdAt = createdAt
            )
        }
    }
}
