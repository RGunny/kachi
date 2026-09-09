package me.rgunny.kachi.collector.support

import me.rgunny.kachi.collector.adapter.outbound.persistence.CollectorOutboxMongoDocument
import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

/**
 * 통합 테스트가 collector_outbox collection을 어댑터를 거치지 않고 직접 다루는 헬퍼.
 *
 * 도메인 저장과 outbox 기록이 한 트랜잭션으로 묶였는지 보려면 outbox 쪽을 따로 준비하고 따로 읽어야 한다.
 */
class CollectorOutboxCollection(
    private val mongoTemplate: ReactiveMongoTemplate
) {

    fun clear() {
        mongoTemplate.remove(CollectorOutboxMongoDocument::class.java).all().block()
    }

    fun insert(outbox: CollectorOutbox) {
        mongoTemplate.insert(CollectorOutboxMongoDocument.fromDomain(outbox)).block()
    }

    fun findAll(): List<CollectorOutboxMongoDocument> {
        return mongoTemplate.findAll(CollectorOutboxMongoDocument::class.java).collectList().block().orEmpty()
    }
}
