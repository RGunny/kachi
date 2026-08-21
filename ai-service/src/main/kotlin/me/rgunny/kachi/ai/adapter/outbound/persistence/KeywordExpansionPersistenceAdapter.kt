package me.rgunny.kachi.ai.adapter.outbound.persistence

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.ai.application.port.outbound.persistence.KeywordExpansionPersistencePort
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import org.springframework.stereotype.Component

@Component
class KeywordExpansionPersistenceAdapter(
    private val repository: KeywordExpansionMongoRepository
) : KeywordExpansionPersistencePort {

    override suspend fun save(keywordExpansion: KeywordExpansion): KeywordExpansion {
        return repository.save(KeywordExpansionMongoDocument.fromDomain(keywordExpansion))
            .awaitSingle()
            .toDomain()
    }
}
