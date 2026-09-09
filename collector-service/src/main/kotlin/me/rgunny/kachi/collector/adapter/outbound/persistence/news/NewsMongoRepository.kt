package me.rgunny.kachi.collector.adapter.outbound.persistence.news

import java.util.UUID
import me.rgunny.kachi.collector.domain.NewsSource
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux

interface NewsMongoRepository : ReactiveMongoRepository<NewsMongoDocument, UUID> {

    fun findBySourceAndUrlHashIn(source: NewsSource, urlHashes: Collection<String>): Flux<NewsMongoDocument>
}
