package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.persistence.KeywordExpansionPersistencePort
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion

class FakeKeywordExpansionPersistencePort : KeywordExpansionPersistencePort {
    val savedExpansions: MutableList<KeywordExpansion> = mutableListOf()

    override suspend fun save(keywordExpansion: KeywordExpansion): KeywordExpansion {
        savedExpansions.add(keywordExpansion)
        return keywordExpansion
    }
}
