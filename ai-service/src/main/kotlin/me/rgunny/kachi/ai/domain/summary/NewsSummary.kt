package me.rgunny.kachi.ai.domain.summary

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import java.time.Instant
import java.util.UUID

class NewsSummary private constructor(
    val id: NewsSummaryId,
    val keyword: AiKeyword,
    val sourceNewsIds: List<UUID>,
    val newsHash: String,
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
            newsHash: String,
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
            require(newsHash.isNotBlank()) { "news hash는 빈 값일 수 없습니다" }
            require(title.isNotBlank()) { "요약 제목은 빈 값일 수 없습니다" }
            require(content.isNotBlank()) { "요약 본문은 빈 값일 수 없습니다" }

            return NewsSummary(
                id = NewsSummaryId.newId(),
                keyword = keyword,
                sourceNewsIds = sourceNewsIds.distinct(),
                newsHash = newsHash,
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
            newsHash: String,
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
                newsHash = newsHash,
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
