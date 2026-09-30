package me.rgunny.kachi.story.support

import me.rgunny.kachi.story.adapter.outbound.persistence.outbox.StoryOutboxMongoDocument
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

/**
 * 통합 테스트가 story_outboxes collection을 어댑터를 거치지 않고 직접 다루는 헬퍼.
 */
class StoryOutboxCollection(
    private val mongoTemplate: ReactiveMongoTemplate
) {

    fun clear() {
        mongoTemplate.remove(StoryOutboxMongoDocument::class.java).all().block()
    }

    fun insert(outbox: StoryOutbox) {
        mongoTemplate.insert(StoryOutboxMongoDocument.fromDomain(outbox)).block()
    }

    fun findAll(): List<StoryOutboxMongoDocument> {
        return mongoTemplate.findAll(StoryOutboxMongoDocument::class.java).collectList().block().orEmpty()
    }
}
