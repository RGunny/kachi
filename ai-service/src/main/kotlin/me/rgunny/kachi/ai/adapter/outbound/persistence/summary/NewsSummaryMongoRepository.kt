package me.rgunny.kachi.ai.adapter.outbound.persistence.summary

import java.util.UUID
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface NewsSummaryMongoRepository : ReactiveMongoRepository<NewsSummaryMongoDocument, UUID> {

    fun findByKeywordAndNewsHashAndPromptVersion(
        keyword: String,
        newsHash: String,
        promptVersion: String
    ): Mono<NewsSummaryMongoDocument>
}
