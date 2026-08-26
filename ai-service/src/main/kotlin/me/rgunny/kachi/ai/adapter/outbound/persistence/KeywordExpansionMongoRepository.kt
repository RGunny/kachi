package me.rgunny.kachi.ai.adapter.outbound.persistence

import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono
import java.util.UUID

interface KeywordExpansionMongoRepository : ReactiveMongoRepository<KeywordExpansionMongoDocument, UUID> {

    fun findByKeywordAndPromptVersion(
        keyword: String,
        promptVersion: String
    ): Mono<KeywordExpansionMongoDocument>
}
