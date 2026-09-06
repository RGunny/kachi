package me.rgunny.kachi.ai.domain.keyword

import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import java.time.Instant

class KeywordExpansion private constructor(
    val id: KeywordExpansionId,
    val keyword: AiKeyword,
    val expandedKeywords: List<ExpandedKeyword>,
    val provider: LlmProvider,
    /** 확장을 만든 모델. 응답이 보고한 모델이고, 보고가 없으면 요청한 모델이다. */
    val model: String,
    /** 요청에 실은 모델. [model]과 다르면 제공자가 다른 모델로 대신 답한 것이다. */
    val requestedModel: String,
    val promptVersion: PromptVersion,
    val createdAt: Instant
) {
    companion object {

        fun create(
            keyword: AiKeyword,
            expandedKeywords: List<ExpandedKeyword>,
            provider: LlmProvider,
            model: String,
            requestedModel: String,
            promptVersion: PromptVersion,
            createdAt: Instant
        ): KeywordExpansion {
            require(model.isNotBlank()) { "확장 모델은 빈 값일 수 없습니다" }
            require(requestedModel.isNotBlank()) { "요청 모델은 빈 값일 수 없습니다" }

            val normalizedExpandedKeywords = expandedKeywords
                .filterNot { it.value.equals(keyword.value, ignoreCase = true) }
                .distinctBy { it.value.lowercase() }

            require(normalizedExpandedKeywords.isNotEmpty()) { "확장 키워드는 하나 이상이어야 합니다" }

            return KeywordExpansion(
                id = KeywordExpansionId.newId(),
                keyword = keyword,
                expandedKeywords = normalizedExpandedKeywords,
                provider = provider,
                model = model,
                requestedModel = requestedModel,
                promptVersion = promptVersion,
                createdAt = createdAt
            )
        }

        fun restore(
            id: KeywordExpansionId,
            keyword: AiKeyword,
            expandedKeywords: List<ExpandedKeyword>,
            provider: LlmProvider,
            model: String,
            requestedModel: String,
            promptVersion: PromptVersion,
            createdAt: Instant
        ): KeywordExpansion {
            return KeywordExpansion(
                id = id,
                keyword = keyword,
                expandedKeywords = expandedKeywords,
                provider = provider,
                model = model,
                requestedModel = requestedModel,
                promptVersion = promptVersion,
                createdAt = createdAt
            )
        }
    }
}
