package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.notification.application.port.outbound.NotificationPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationHistoryId
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationHistoryDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * NotificationPersistencePort MongoDB 구현.
 *
 * claim 계열은 find -> markProcessing -> save로 구현하지 않는다.
 * `_id + 현재 status` 조건을 가진 findAndModify로 처리해서 다중 worker 환경의 중복 claim을 막는다.
 */
@Repository
class NotificationMongoPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val mapper: NotificationDocumentMapper,
) : NotificationPersistencePort {

    override suspend fun save(notification: Notification): Notification {
        return mongoTemplate.save(mapper.toDocument(notification))
            .map(mapper::toDomain)
            .awaitSingle()
    }

    override suspend fun findById(notificationId: NotificationId): Notification? {
        return mongoTemplate.findById(notificationId.id.toString(), NotificationDocument::class.java)
            .map(mapper::toDomain)
            .awaitSingleOrNull()
    }

    override suspend fun findByRequestId(requestId: String): Notification? {
        val query = Query.query(Criteria.where(FIELD_REQUEST_ID).`is`(requestId))

        return mongoTemplate.findOne(query, NotificationDocument::class.java)
            .map(mapper::toDomain)
            .awaitSingleOrNull()
    }

    override suspend fun claimFromPublished(
        notificationId: NotificationId,
        workerId: String,
        now: Instant,
    ): Notification? {
        return claimProcessing(
            notificationId = notificationId,
            fromStatus = NotificationStatus.PUBLISHED,
            workerId = workerId,
            now = now,
        )
    }

    override suspend fun claimFromRetryWait(
        notificationId: NotificationId,
        workerId: String,
        now: Instant,
    ): Notification? {
        return claimProcessing(
            notificationId = notificationId,
            fromStatus = NotificationStatus.RETRY_WAIT,
            workerId = workerId,
            now = now,
        )
    }

    private suspend fun claimProcessing(
        notificationId: NotificationId,
        fromStatus: NotificationStatus,
        workerId: String,
        now: Instant,
    ): Notification? {
        require(workerId.isNotBlank()) { "workerId must not be blank" }

        val query = Query.query(
            Criteria.where(FIELD_ID).`is`(notificationId.id.toString())
                .and(FIELD_STATUS).`is`(fromStatus.name)
        )
        val update = Update()
            .set(FIELD_STATUS, NotificationStatus.PROCESSING.name)
            .set(FIELD_FAILURE_REASON, null)
            .set(FIELD_UPDATED_AT, now)
            .set(FIELD_LAST_TRANSITION_AT, now)
            .set(FIELD_CLAIMED_AT, now)
            .set(FIELD_CLAIMED_BY, workerId)
            .push(
                FIELD_HISTORIES,
                NotificationHistoryDocument(
                    id = NotificationHistoryId.newId().id.toString(),
                    notificationId = notificationId.id.toString(),
                    fromStatus = fromStatus.name,
                    toStatus = NotificationStatus.PROCESSING.name,
                    reason = null,
                    createdAt = now,
                )
            )

        return mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            NotificationDocument::class.java,
        )
            .map(mapper::toDomain)
            .awaitSingleOrNull()
    }

    private companion object {
        const val FIELD_ID = "_id"
        const val FIELD_STATUS = "status"
        const val FIELD_FAILURE_REASON = "failureReason"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_LAST_TRANSITION_AT = "lastTransitionAt"
        const val FIELD_CLAIMED_AT = "claimedAt"
        const val FIELD_CLAIMED_BY = "claimedBy"
        const val FIELD_HISTORIES = "histories"
        const val FIELD_REQUEST_ID = "requestId"
    }
}
