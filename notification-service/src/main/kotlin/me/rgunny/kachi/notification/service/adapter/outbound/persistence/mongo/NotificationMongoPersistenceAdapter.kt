package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import com.mongodb.MongoCommandException
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.notification.application.port.outbound.NotificationPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationHistory
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationHistoryDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
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
    private val transactionalOperator: TransactionalOperator,
) : NotificationPersistencePort {

    override suspend fun save(notification: Notification): Notification {
        return transactionalOperator.executeAndAwait {
            val saved = mongoTemplate.save(mapper.toDocument(notification))
                .map(mapper::toDomain)
                .awaitSingle()

            insertHistories(notification.uncommittedHistories)

            return@executeAndAwait saved
        }
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

    /**
     * recovery 대상인 오래된 PROCESSING 알림을 claimedAt 오름차순으로 제한 조회한다.
     */
    override suspend fun findStaleProcessing(
        threshold: Instant,
        batchSize: Int,
    ): List<Notification> {
        // PROCESSING claim 이후 threshold까지 finalize되지 않은 알림만 recovery 대상으로 조회한다.
        // 오래된 claim부터 처리해 같은 tick에서 일부만 처리돼도 가장 오래 막힌 건부터 풀리게 한다.
        val query = Query.query(
            Criteria.where(FIELD_STATUS).`is`(NotificationStatus.PROCESSING.name)
                .and(FIELD_CLAIMED_AT).lt(threshold)
        )
            .with(Sort.by(Sort.Order.asc(FIELD_CLAIMED_AT)))
            .limit(batchSize)

        return mongoTemplate.find(query, NotificationDocument::class.java)
            .map(mapper::toDomain)
            .collectList()
            .awaitSingle()
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

        return try {
            transactionalOperator.executeAndAwait {
                val history = NotificationHistory.record(
                    notificationId = notificationId,
                    fromStatus = fromStatus,
                    toStatus = NotificationStatus.PROCESSING,
                    createdAt = now,
                    reason = null,
                )

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

                val claimed = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    NotificationDocument::class.java,
                )
                    .map(mapper::toDomain)
                    .awaitSingleOrNull()

                if (claimed != null) {
                    insertHistory(history)
                }

                return@executeAndAwait claimed
            }
        } catch (exception: DataAccessException) {
            if (exception.isMongoWriteConflict()) {
                // 같은 row를 두고 claim transaction이 경합하면 MongoDB가 WriteConflict를 반환할 수 있다.
                // claim CAS에서는 같은 조건 재시도가 대부분 상태 불일치로 귀결되므로 claim 실패로 다룬다.
                null
            } else {
                throw exception
            }
        }
    }

    private suspend fun insertHistories(histories: List<NotificationHistory>) {
        histories.forEach { insertHistory(it) }
    }

    private suspend fun insertHistory(history: NotificationHistory) {
        mongoTemplate.insert(mapper.toHistoryDocument(history))
            .awaitSingle()
    }

    private companion object {
        const val FIELD_ID = "_id"
        const val FIELD_STATUS = "status"
        const val FIELD_FAILURE_REASON = "failureReason"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_LAST_TRANSITION_AT = "lastTransitionAt"
        const val FIELD_CLAIMED_AT = "claimedAt"
        const val FIELD_CLAIMED_BY = "claimedBy"
        const val FIELD_REQUEST_ID = "requestId"
    }
}

private fun Throwable.isMongoWriteConflict(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is MongoCommandException && current.code == 112) {
            return true
        }
        current = current.cause
    }
    return false
}
