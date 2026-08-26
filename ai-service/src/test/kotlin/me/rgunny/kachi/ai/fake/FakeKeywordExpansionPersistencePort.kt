package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.persistence.KeywordExpansionPersistencePort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import me.rgunny.kachi.ai.domain.llm.PromptVersion

class FakeKeywordExpansionPersistencePort : KeywordExpansionPersistencePort {
    val savedExpansions: MutableList<KeywordExpansion> = mutableListOf()
    val existingExpansions: MutableList<KeywordExpansion> = mutableListOf()
    var duplicateOnSave: Boolean = false
    var existingAfterDuplicate: KeywordExpansion? = null
    var saveOrFindExistingCallCount: Int = 0

    override suspend fun findByUniqueKey(
        keyword: AiKeyword,
        promptVersion: PromptVersion
    ): KeywordExpansion? {
        return existingExpansions.firstOrNull {
            it.keyword == keyword && it.promptVersion == promptVersion
        }
    }

    override suspend fun save(keywordExpansion: KeywordExpansion): KeywordExpansion {
        savedExpansions.add(keywordExpansion)
        return keywordExpansion
    }

    override suspend fun saveOrFindExisting(keywordExpansion: KeywordExpansion): KeywordExpansion {
        saveOrFindExistingCallCount += 1

        if (duplicateOnSave) {
            return existingAfterDuplicate ?: keywordExpansion
        }

        return save(keywordExpansion)
    }
}
