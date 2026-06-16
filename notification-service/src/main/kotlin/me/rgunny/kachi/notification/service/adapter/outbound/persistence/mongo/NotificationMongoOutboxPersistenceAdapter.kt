package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.notification.application.port.outbound.NotificationOutboxPersistencePort
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxId
import me.rgunny.kachi.notification.domain.NotificationOutboxStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationOutboxDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationOutboxDocumentMapper
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * NotificationOutboxPersistencePort MongoDB 구현.
 *
 * claimPublishing은 find -> markPublishing -> save로 구현하지 않는다.
 * `_id + PENDING` 조건을 가진 findAndModify로 처리해서 outbox 중복 발행을 막는다.
 */
@Repository
class NotificationMongoOutboxPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val mapper: NotificationOutboxDocumentMapper,
) : NotificationOutboxPersistencePort {

    override suspend fun save(outbox: NotificationOutbox): NotificationOutbox {
        return mongoTemplate.save(mapper.toDocument(outbox))
            .map(mapper::toDomain)
            .awaitSingle()
    }

    override suspend fun findById(outboxId: NotificationOutboxId): NotificationOutbox? {
        return mongoTemplate.findById(outboxId.id.toString(), NotificationOutboxDocument::class.java)
            .map(mapper::toDomain)
            .awaitSingleOrNull()
    }

    override suspend fun findPublishable(now: Instant, batchSize: Int): List<NotificationOutbox> {
        val query = Query.query(
            Criteria.where(FIELD_OUTBOX_STATUS).`is`(NotificationOutboxStatus.PENDING.name)
                .and(FIELD_NEXT_RETRY_AT).lte(now)
        )
            .with(Sort.by(Sort.Order.asc(FIELD_NEXT_RETRY_AT), Sort.Order.asc(FIELD_CREATED_AT)))
            .limit(batchSize)

        return mongoTemplate.find(query, NotificationOutboxDocument::class.java)
            .map(mapper::toDomain)
            .collectList()
            .awaitSingle()
    }

    override suspend fun claimPublishing(
        outboxId: NotificationOutboxId,
        claimedBy: String,
        now: Instant,
    ): NotificationOutbox? {
        require(claimedBy.isNotBlank()) { "claimedBy must not be blank" }

        val query = Query.query(
            Criteria.where(FIELD_ID).`is`(outboxId.id.toString())
                .and(FIELD_OUTBOX_STATUS).`is`(NotificationOutboxStatus.PENDING.name)
                .and(FIELD_NEXT_RETRY_AT).lte(now)
        )
        val update = Update()
            .set(FIELD_OUTBOX_STATUS, NotificationOutboxStatus.PUBLISHING.name)
            .set(FIELD_CLAIMED_AT, now)
            .set(FIELD_CLAIMED_BY, claimedBy)

        return mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            NotificationOutboxDocument::class.java,
        )
            .map(mapper::toDomain)
            .awaitSingleOrNull()
    }

    override suspend fun findStalePublishing(
        threshold: Instant,
        batchSize: Int,
    ): List<NotificationOutbox> {
        val query = Query.query(
            Criteria.where(FIELD_OUTBOX_STATUS).`is`(NotificationOutboxStatus.PUBLISHING.name)
                .and(FIELD_CLAIMED_AT).lt(threshold)
        )
            .with(Sort.by(Sort.Order.asc(FIELD_CLAIMED_AT)))
            .limit(batchSize)

        return mongoTemplate.find(query, NotificationOutboxDocument::class.java)
            .map(mapper::toDomain)
            .collectList()
            .awaitSingle()
    }

    private companion object {
        const val FIELD_ID = "_id"
        const val FIELD_OUTBOX_STATUS = "outboxStatus"
        const val FIELD_NEXT_RETRY_AT = "nextRetryAt"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_CLAIMED_AT = "claimedAt"
        const val FIELD_CLAIMED_BY = "claimedBy"
    }
}
