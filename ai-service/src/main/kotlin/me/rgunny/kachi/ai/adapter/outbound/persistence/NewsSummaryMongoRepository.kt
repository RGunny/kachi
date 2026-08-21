package me.rgunny.kachi.ai.adapter.outbound.persistence

import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono
import java.util.UUID

interface NewsSummaryMongoRepository : ReactiveMongoRepository<NewsSummaryMongoDocument, UUID> {

    fun findByKeywordAndNewsHashAndPromptVersionAndModel(
        keyword: String,
        newsHash: String,
        promptVersion: String,
        model: String
    ): Mono<NewsSummaryMongoDocument>
}
