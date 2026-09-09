package me.rgunny.kachi.ai.support

import me.rgunny.kachi.ai.adapter.outbound.persistence.outbox.AiOutboxMongoDocument
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

/**
 * 통합 테스트가 ai_outbox collection을 어댑터를 거치지 않고 직접 다루는 헬퍼.
 *
 * 도메인 저장과 outbox 기록이 한 트랜잭션으로 묶였는지 보려면 outbox 쪽을 따로 준비하고 따로 읽어야 한다.
 */
class AiOutboxCollection(
    private val mongoTemplate: ReactiveMongoTemplate
) {

    fun clear() {
        mongoTemplate.remove(AiOutboxMongoDocument::class.java).all().block()
    }

    fun insert(outbox: AiOutbox) {
        mongoTemplate.insert(AiOutboxMongoDocument.fromDomain(outbox)).block()
    }

    fun findAll(): List<AiOutboxMongoDocument> {
        return mongoTemplate.findAll(AiOutboxMongoDocument::class.java).collectList().block().orEmpty()
    }
}
