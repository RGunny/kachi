package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationAdminPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationHistory
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationHistoryDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationOutboxDocumentMapper
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

/**
 * Notification 현재 상태 document 운영 조회/복구 MongoDB 구현.
 *
 * DEAD notification과 상태 history를 조회하고, 수동 복구 시 새 dispatch outbox를 같은 transaction에서 생성한다.
 */
@Repository
class NotificationMongoAdminPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val notificationMapper: NotificationDocumentMapper,
    private val outboxMapper: NotificationOutboxDocumentMapper,
    private val transactionalOperator: TransactionalOperator,
) : NotificationAdminPersistencePort {

    override suspend fun findDead(batchSize: Int): List<Notification> {
        return mongoTemplate.find(
            Query.query(Criteria.where(FIELD_STATUS).`is`(NotificationStatus.DEAD.name))
                .with(Sort.by(Sort.Direction.DESC, FIELD_UPDATED_AT))
                .limit(batchSize),
            NotificationDocument::class.java,
        )
            .map(notificationMapper::toDomain)
            .collectList()
            .awaitSingle()
    }

    override suspend fun findHistories(
        notificationId: NotificationId,
        batchSize: Int,
    ): List<NotificationHistory> {
        return mongoTemplate.find(
            Query.query(Criteria.where(FIELD_NOTIFICATION_ID).`is`(notificationId.id.toString()))
                .with(Sort.by(Sort.Direction.ASC, FIELD_CREATED_AT))
                .limit(batchSize),
            NotificationHistoryDocument::class.java,
        )
            .map(notificationMapper::toHistoryDomain)
            .collectList()
            .awaitSingle()
    }

    /**
     * DEAD 조건이 맞는 경우에만 notification, history, outbox를 하나의 transaction으로 확정한다.
     */
    override suspend fun recoverDeadToRequested(
        notification: Notification,
        outbox: NotificationOutbox,
    ): Notification? {
        return transactionalOperator.executeAndAwait {
            // 1. core가 계산한 복구 후 상태 snapshot을 Mongo update 값으로 변환한다.
            val document = notificationMapper.toDocument(notification)

            // 2. 저장소의 현재 상태가 아직 DEAD일 때만 REQUESTED로 조건부 갱신한다.
            val recovered = mongoTemplate.findAndModify(
                Query.query(
                    Criteria.where(FIELD_ID).`is`(document.id)
                        .and(FIELD_STATUS).`is`(NotificationStatus.DEAD.name)
                ),
                Update()
                    .set(FIELD_STATUS, document.status)
                    .set(FIELD_FAILURE_REASON, document.failureReason)
                    .set(FIELD_UPDATED_AT, document.updatedAt)
                    .set(FIELD_LAST_TRANSITION_AT, document.lastTransitionAt)
                    .set(FIELD_DISPATCH_ATTEMPTS, document.dispatchAttempts)
                    .set(FIELD_CLAIMED_AT, null)
                    .set(FIELD_CLAIMED_BY, null),
                FindAndModifyOptions.options().returnNew(true),
                NotificationDocument::class.java,
            )
                .map(notificationMapper::toDomain)
                .awaitSingleOrNull()

            // 3. 조건부 갱신에 성공한 경우에만 상태 전이 history와 새 outbox를 같은 transaction에 저장한다.
            if (recovered != null) {
                insertUncommittedHistories(notification)
                mongoTemplate.insert(outboxMapper.toDocument(outbox))
                    .awaitSingle()
            }

            // 4. 조건 불일치면 null을 반환해 caller가 stale 상태로 판단하게 한다.
            return@executeAndAwait recovered
        }
    }

    /**
     * domain이 이번 처리에서 만든 상태 전이 이력을 별도 collection에 append한다.
     */
    private suspend fun insertUncommittedHistories(notification: Notification) {
        notification.uncommittedHistories.forEach { history ->
            mongoTemplate.insert(notificationMapper.toHistoryDocument(history))
                .awaitSingle()
        }
    }

    private companion object {
        const val FIELD_ID = "_id"
        const val FIELD_STATUS = "status"
        const val FIELD_FAILURE_REASON = "failureReason"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_LAST_TRANSITION_AT = "lastTransitionAt"
        const val FIELD_DISPATCH_ATTEMPTS = "dispatchAttempts"
        const val FIELD_CLAIMED_AT = "claimedAt"
        const val FIELD_CLAIMED_BY = "claimedBy"
        const val FIELD_NOTIFICATION_ID = "notificationId"
        const val FIELD_CREATED_AT = "createdAt"
    }
}
