package me.rgunny.kachi.ai.domain.summary

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.story.StoryId
import java.time.Instant
import java.util.UUID

/**
 * story 하나의 요약 버전 하나.
 *
 * (storyId, version)이 멱등 키다.
 * 버전은 1부터 시작해 1씩 커진다.
 * [newNewsIds]는 이 버전에서 새로 반영된 기사이고, [sourceNewsCount]는 이 버전까지 반영된 기사 수 누적이다.
 */
class StorySummary private constructor(
    val id: StorySummaryId,
    val storyId: StoryId,
    val version: Long,
    val keywords: List<AiKeyword>,
    val newNewsIds: List<UUID>,
    val sourceNewsCount: Int,
    val title: String,
    val content: String,
    val sentiment: NewsSummarySentiment,
    val developmentKind: StoryDevelopmentKind,
    val provider: LlmProvider,
    /** 요약을 만든 모델(응답이 보고한 모델, 보고가 없으면 요청 모델). */
    val model: String,
    /** 요청에 실은 모델([model]과 다르면 제공자가 대체 모델로 응답). */
    val requestedModel: String,
    val promptVersion: PromptVersion,
    val tokenUsage: TokenUsage,
    val createdAt: Instant
) {

    companion object {

        fun create(
            storyId: StoryId,
            version: Long,
            keywords: List<AiKeyword>,
            newNewsIds: List<UUID>,
            sourceNewsCount: Int,
            title: String,
            content: String,
            sentiment: NewsSummarySentiment,
            developmentKind: StoryDevelopmentKind,
            provider: LlmProvider,
            model: String,
            requestedModel: String,
            promptVersion: PromptVersion,
            tokenUsage: TokenUsage,
            createdAt: Instant
        ): StorySummary {
            require(version >= 1) { "요약 버전은 1 이상이어야 합니다: $version" }
            require(version > 1 || developmentKind == StoryDevelopmentKind.DEVELOPMENT) {
                "첫 버전은 비교할 직전 요약이 없으므로 DEVELOPMENT여야 합니다: $developmentKind"
            }
            require(keywords.isNotEmpty()) { "요약 키워드는 하나 이상이어야 합니다" }
            require(newNewsIds.isNotEmpty()) { "이 버전에 반영된 새 기사는 하나 이상이어야 합니다" }
            require(sourceNewsCount >= newNewsIds.size) {
                "누적 기사 수는 새 기사 수 이상이어야 합니다: source=$sourceNewsCount, new=${newNewsIds.size}"
            }
            require(title.isNotBlank()) { "요약 제목은 빈 값일 수 없습니다" }
            require(content.isNotBlank()) { "요약 본문은 빈 값일 수 없습니다" }
            require(model.isNotBlank()) { "요약 모델은 빈 값일 수 없습니다" }
            require(requestedModel.isNotBlank()) { "요청 모델은 빈 값일 수 없습니다" }

            return StorySummary(
                id = StorySummaryId.newId(),
                storyId = storyId,
                version = version,
                keywords = keywords.distinct(),
                newNewsIds = newNewsIds.distinct(),
                sourceNewsCount = sourceNewsCount,
                title = title.trim(),
                content = content.trim(),
                sentiment = sentiment,
                developmentKind = developmentKind,
                provider = provider,
                model = model,
                requestedModel = requestedModel,
                promptVersion = promptVersion,
                tokenUsage = tokenUsage,
                createdAt = createdAt
            )
        }

        fun restore(
            id: StorySummaryId,
            storyId: StoryId,
            version: Long,
            keywords: List<AiKeyword>,
            newNewsIds: List<UUID>,
            sourceNewsCount: Int,
            title: String,
            content: String,
            sentiment: NewsSummarySentiment,
            developmentKind: StoryDevelopmentKind,
            provider: LlmProvider,
            model: String,
            requestedModel: String,
            promptVersion: PromptVersion,
            tokenUsage: TokenUsage,
            createdAt: Instant
        ): StorySummary {
            return StorySummary(
                id = id,
                storyId = storyId,
                version = version,
                keywords = keywords,
                newNewsIds = newNewsIds,
                sourceNewsCount = sourceNewsCount,
                title = title,
                content = content,
                sentiment = sentiment,
                developmentKind = developmentKind,
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
