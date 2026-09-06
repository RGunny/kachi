package me.rgunny.kachi.ai.domain.summary

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmProvider
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
    val provider: LlmProvider,
    /** 요약을 만든 모델. 응답이 보고한 모델이고, 보고가 없으면 요청한 모델이다. */
    val model: String,
    /** 요청에 실은 모델. [model]과 다르면 제공자가 다른 모델로 대신 답한 것이다. */
    val requestedModel: String,
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
            provider: LlmProvider,
            model: String,
            requestedModel: String,
            promptVersion: PromptVersion,
            tokenUsage: TokenUsage,
            createdAt: Instant
        ): NewsSummary {
            require(sourceNewsIds.isNotEmpty()) { "요약 대상 뉴스는 하나 이상이어야 합니다" }
            require(newsHash.isNotBlank()) { "news hash는 빈 값일 수 없습니다" }
            require(title.isNotBlank()) { "요약 제목은 빈 값일 수 없습니다" }
            require(content.isNotBlank()) { "요약 본문은 빈 값일 수 없습니다" }
            require(model.isNotBlank()) { "요약 모델은 빈 값일 수 없습니다" }
            require(requestedModel.isNotBlank()) { "요청 모델은 빈 값일 수 없습니다" }

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
                requestedModel = requestedModel,
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
            provider: LlmProvider,
            model: String,
            requestedModel: String,
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
                requestedModel = requestedModel,
                promptVersion = promptVersion,
                tokenUsage = tokenUsage,
                createdAt = createdAt
            )
        }
    }
}
