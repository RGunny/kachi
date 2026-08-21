package me.rgunny.kachi.ai.adapter.outbound.persistence

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansionId
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document(collection = "keyword_expansions")
@CompoundIndexes(
    CompoundIndex(
        name = "ux_keyword_expansions_keyword_prompt_model",
        def = "{'keyword': 1, 'promptVersion': 1, 'model': 1}",
        unique = true
    )
)
data class KeywordExpansionMongoDocument(
    @Id
    val id: UUID,
    @Indexed
    val keyword: String,
    val expandedKeywords: List<String>,
    val provider: String,
    val model: String,
    val promptVersion: String,
    @Indexed
    val createdAt: Instant
) {
    fun toDomain(): KeywordExpansion {
        return KeywordExpansion.restore(
            id = KeywordExpansionId.of(id),
            keyword = AiKeyword.of(keyword),
            expandedKeywords = expandedKeywords.map(ExpandedKeyword::of),
            provider = LlmProviderName.of(provider),
            model = LlmModelName.of(model),
            promptVersion = PromptVersion.of(promptVersion),
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(keywordExpansion: KeywordExpansion): KeywordExpansionMongoDocument {
            return KeywordExpansionMongoDocument(
                id = keywordExpansion.id.value,
                keyword = keywordExpansion.keyword.value,
                expandedKeywords = keywordExpansion.expandedKeywords.map { it.value },
                provider = keywordExpansion.provider.value,
                model = keywordExpansion.model.value,
                promptVersion = keywordExpansion.promptVersion.value,
                createdAt = keywordExpansion.createdAt
            )
        }
    }
}
