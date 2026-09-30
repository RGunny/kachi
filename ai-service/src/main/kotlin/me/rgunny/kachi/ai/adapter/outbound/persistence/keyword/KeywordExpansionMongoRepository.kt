package me.rgunny.kachi.ai.adapter.outbound.persistence.keyword

import java.util.UUID
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface KeywordExpansionMongoRepository : ReactiveMongoRepository<KeywordExpansionMongoDocument, UUID> {

    fun findByKeywordAndPromptVersion(
        keyword: String,
        promptVersion: String
    ): Mono<KeywordExpansionMongoDocument>
}
