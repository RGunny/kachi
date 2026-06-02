package me.rgunny.kachi.ai.domain

import java.time.Instant

class KeywordExpansion private constructor(
    val id: KeywordExpansionId,
    val keyword: AiKeyword,
    val expandedKeywords: List<ExpandedKeyword>,
    val provider: LlmProviderName,
    val model: LlmModelName,
    val promptVersion: PromptVersion,
    val createdAt: Instant
) {
    companion object {

        fun create(
            keyword: AiKeyword,
            expandedKeywords: List<ExpandedKeyword>,
            provider: LlmProviderName,
            model: LlmModelName,
            promptVersion: PromptVersion,
            createdAt: Instant
        ): KeywordExpansion {
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
                promptVersion = promptVersion,
                createdAt = createdAt
            )
        }

        fun restore(
            id: KeywordExpansionId,
            keyword: AiKeyword,
            expandedKeywords: List<ExpandedKeyword>,
            provider: LlmProviderName,
            model: LlmModelName,
            promptVersion: PromptVersion,
            createdAt: Instant
        ): KeywordExpansion {
            return KeywordExpansion(
                id = id,
                keyword = keyword,
                expandedKeywords = expandedKeywords,
                provider = provider,
                model = model,
                promptVersion = promptVersion,
                createdAt = createdAt
            )
        }
    }
}
