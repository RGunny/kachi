package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mongo

import com.mongodb.MongoCommandException
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationHistory
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.document.NotificationDocument
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.document.NotificationHistoryDocument
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.dao.DataAccessException
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.time.Instant

/**
 * worker용 NotificationPersistencePort MongoDB 구현.
 *
 * dispatch claim은 `_id + status` 조건의 findAndModify로 처리한다.
 * Kafka는 at-least-once라 같은 dispatch 메시지가 retry/rebalance/replay로 다시 들어올 수 있다.
 * 이때 PUBLISHED 또는 RETRY_WAIT 상태인 알림만 PROCESSING으로 바꿔 처리권을 얻고,
 * 이미 PROCESSING/SENT/DEAD 등으로 바뀐 알림은 claim에 실패하게 해서 중복 vendor 호출을 막는다.
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
        // vendor 호출 또는 DB finalize 전에 worker가 중단되면 PROCESSING 상태가 남을 수 있다.
        // claimedAt이 visibility timeout을 넘긴 알림만 recovery 대상으로 보고, fresh PROCESSING은 건드리지 않는다.
        // 오래된 claim부터 회수해 장시간 막힌 dispatch를 우선 재시도/DEAD 처리한다.
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
        return claimProcessing(notificationId, NotificationStatus.PUBLISHED, workerId, now)
    }

    override suspend fun claimFromRetryWait(
        notificationId: NotificationId,
        workerId: String,
        now: Instant,
    ): Notification? {
        return claimProcessing(notificationId, NotificationStatus.RETRY_WAIT, workerId, now)
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

                // 1. claim 대상은 notificationId와 현재 상태를 함께 조건으로 건다.
                // 이미 다른 worker가 PROCESSING으로 바꿨거나 terminal 상태가 된 row는 여기서 제외된다.
                val query = Query.query(
                    Criteria.where(FIELD_ID).`is`(notificationId.id.toString())
                        .and(FIELD_STATUS).`is`(fromStatus.name)
                )

                // 2. 상태 전이와 claim 정보를 하나의 findAndModify update로 묶는다.
                // history는 claim 성공이 확인된 뒤 같은 transaction 안에서 별도 collection에 append한다.
                val update = Update()
                    .set(FIELD_STATUS, NotificationStatus.PROCESSING.name)
                    .set(FIELD_FAILURE_REASON, null)
                    .set(FIELD_UPDATED_AT, now)
                    .set(FIELD_LAST_TRANSITION_AT, now)
                    .set(FIELD_CLAIMED_AT, now)
                    .set(FIELD_CLAIMED_BY, workerId)

                // 3. 조건에 맞는 row 하나만 원자적으로 PROCESSING으로 바꾸고, 갱신된 document를 domain으로 복원한다.
                // 조건 불일치면 null을 반환해 caller가 이미 처리 중/처리 완료된 메시지로 판단하게 한다.
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
