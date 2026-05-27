package me.rgunny.kachi.collector.adapter.out.persistence

import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import java.util.UUID

interface NewsMongoRepository : ReactiveMongoRepository<NewsMongoDocument, UUID> {

    fun findByUrlHashIn(urlHashes: Collection<String>): Flux<NewsMongoDocument>
}
