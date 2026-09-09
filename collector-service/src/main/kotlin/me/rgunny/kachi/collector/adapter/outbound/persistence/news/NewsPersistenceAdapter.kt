package me.rgunny.kachi.collector.adapter.outbound.persistence.news

import java.time.Instant
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.collector.adapter.outbound.persistence.outbox.CollectorOutboxMongoDocument
import me.rgunny.kachi.collector.application.port.outbound.news.NewsPersistencePort
import me.rgunny.kachi.collector.application.port.outbound.news.model.SaveNewsResult
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.News
import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Component
class NewsPersistenceAdapter(
    private val repository: NewsMongoRepository,
    private val mongoTemplate: ReactiveMongoTemplate,
    private val transactionalOperator: TransactionalOperator
) : NewsPersistencePort {

    override suspend fun findExistingUrlHashes(source: NewsSource, urlHashes: Set<String>): Set<String> {
        if (urlHashes.isEmpty()) return emptySet()

        return repository.findBySourceAndUrlHashIn(source, urlHashes)
            .map { it.urlHash }
            .collectList()
            .awaitSingle()
            .toSet()
    }

    /**
     * 저장된 뉴스 중 matchedKeywords에 keyword가 포함된 항목을 조회한다.
     *
     * from/to가 있으면 collectedAt 기준 기간 조건을 적용하고,
     * 최신 수집 뉴스가 먼저 오도록 collectedAt 내림차순으로 정렬한다.
     */
    override suspend fun findByKeyword(
        keyword: CollectedKeyword,
        from: Instant?,
        to: Instant?,
        limit: Int
    ): List<News> {
        val criteria = mutableListOf(Criteria.where("matchedKeywords").`is`(keyword.value))
        val collectedAtCriteria = Criteria.where("collectedAt")
        when {
            from != null && to != null -> criteria.add(collectedAtCriteria.gte(from).lte(to))
            from != null -> criteria.add(collectedAtCriteria.gte(from))
            to != null -> criteria.add(collectedAtCriteria.lte(to))
        }

        val query = Query(Criteria().andOperator(criteria))
            .with(Sort.by(Sort.Direction.DESC, "collectedAt"))
            .limit(limit)

        return mongoTemplate.find(query, NewsMongoDocument::class.java)
            .map(NewsMongoDocument::toDomain)
            .collectList()
            .awaitSingleOrNull()
            ?: emptyList()
    }

    /**
     * 기사 insert와 outbox insert를 한 트랜잭션에 둔다. 트랜잭션은 replica set 전제다(ADR 017).
     * 같은 출처·URL의 unique index 충돌은 트랜잭션 전체를 되돌리므로 outbox 행도 남지 않는다.
     */
    override suspend fun save(news: News, outbox: CollectorOutbox): SaveNewsResult {
        return try {
            transactionalOperator.executeAndAwait {
                mongoTemplate.insert(NewsMongoDocument.fromDomain(news)).awaitSingle()
                mongoTemplate.insert(CollectorOutboxMongoDocument.fromDomain(outbox)).awaitSingle()
            }
            SaveNewsResult.SAVED
        } catch (e: DuplicateKeyException) {
            SaveNewsResult.DUPLICATED
        }
    }
}
