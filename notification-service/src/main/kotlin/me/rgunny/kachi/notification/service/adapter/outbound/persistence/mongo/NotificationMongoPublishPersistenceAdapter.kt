package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationPublishPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationHistoryDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationOutboxDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationOutboxDocumentMapper
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.time.Instant

/**
 * notification.dispatch 발행 결과 저장 MongoDB 구현.
 *
 * 이 adapter가 outbox publish 흐름의 MongoDB finalize 경계다.
 *
 * 전체 publish 흐름은 다음처럼 분리한다.
 *
 * 1. OutboxPersistencePort.claimPublishing()이 PENDING outbox를 PUBLISHING으로 선점한다.
 * 2. Kafka publish는 MongoDB transaction 밖에서 실행한다.
 * 3. Kafka publish 결과만 이 adapter가 MongoDB transaction으로 확정한다.
 *
 * Spring/Mongo transaction은 Kafka publish, Redis, 외부 API 같은 side effect를 rollback하지 못한다.
 * 그래서 이 adapter는 Kafka 호출을 절대 포함하지 않고, 이미 끝난 publish 결과를 DB 상태로 맞추는 일만 담당한다.
 *
 * 성공 finalize:
 *
 * - PUBLISHING outbox를 PUBLISHED 상태로 저장한다.
 * - 연결된 Notification을 PUBLISHED 상태로 전이한다.
 *
 * 실패 finalize:
 *
 * - retry 정책이 반영된 outbox(PENDING 또는 DEAD)를 저장한다.
 * - 연결된 Notification을 PUBLISH_FAILED 상태로 전이한다.
 *
 * 두 document 중 하나만 반영되면 scheduler가 같은 outbox를 재처리하거나 notification 상태가 어긋날 수 있다.
 * 따라서 두 저장은 반드시 하나의 MongoDB transaction 안에서 끝나야 한다.
 *
 * outbox 저장은 덮어쓰기가 아니라 `_id + PUBLISHING + claim` 조건부 갱신이다.
 * publish가 visibility timeout보다 오래 걸려 다른 tick이 그 행을 회수한 뒤에 결과가 도착하면 조건이 어긋난다.
 * 그때는 outbox도 notification도 건드리지 않고 false를 돌려주어, 회수 이후의 상태가 늦은 결과에 덮이지 않게 한다.
 */
@Repository
class NotificationMongoPublishPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val notificationMapper: NotificationDocumentMapper,
    private val outboxMapper: NotificationOutboxDocumentMapper,
    private val transactionalOperator: TransactionalOperator,
) : NotificationPublishPersistencePort {

    override suspend fun savePublished(
        outbox: NotificationOutbox,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
        now: Instant,
    ): Boolean {
        return transactionalOperator.executeAndAwait {
            // 1. 발행을 시작할 때 잡은 claim이 그대로일 때만 발행 완료 상태를 확정한다.
            if (!finalizeOutbox(outbox, expectedClaimedAt, expectedClaimedBy)) {
                return@executeAndAwait false
            }

            // 2. 같은 transaction 안에서 notification도 publish 완료 상태로 맞춘다.
            val notification = findNotification(outbox)
            notification.markPublished(now)
            mongoTemplate.save(notificationMapper.toDocument(notification))
                .awaitSingle()
            insertUncommittedHistories(notification)

            true
        } ?: false
    }

    override suspend fun savePublishFailed(
        outbox: NotificationOutbox,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
        now: Instant,
        reason: String,
    ): Boolean {
        return transactionalOperator.executeAndAwait {
            // 1. retry/DEAD 계산이 끝난 실패 상태도 같은 claim 조건으로만 확정한다.
            if (!finalizeOutbox(outbox, expectedClaimedAt, expectedClaimedBy)) {
                return@executeAndAwait false
            }

            // 2. 같은 transaction 안에서 notification에도 publish 실패 사유를 남긴다.
            val notification = findNotification(outbox)
            notification.markPublishFailed(now, reason)
            mongoTemplate.save(notificationMapper.toDocument(notification))
                .awaitSingle()
            insertUncommittedHistories(notification)

            true
        } ?: false
    }

    /**
     * 전이가 끝난 outbox 상태를 claim 조건부로 반영한다. 조건이 어긋나면 아무것도 바꾸지 않고 false를 돌려준다.
     */
    private suspend fun finalizeOutbox(
        outbox: NotificationOutbox,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
    ): Boolean {
        val query = Query.query(
            Criteria.where(FIELD_ID).`is`(outbox.id.id.toString())
                .and(FIELD_OUTBOX_STATUS).`is`(NotificationOutboxStatus.PUBLISHING.name)
                .and(FIELD_CLAIMED_AT).`is`(expectedClaimedAt)
                .and(FIELD_CLAIMED_BY).`is`(expectedClaimedBy)
        )

        val finalized = mongoTemplate.findAndModify(
            query,
            finalizedUpdate(outbox),
            FindAndModifyOptions.options().returnNew(true),
            NotificationOutboxDocument::class.java,
        )
            .awaitSingleOrNull()

        return finalized != null
    }

    private fun finalizedUpdate(outbox: NotificationOutbox): Update {
        val document = outboxMapper.toDocument(outbox)

        return Update()
            .set(FIELD_OUTBOX_STATUS, document.outboxStatus)
            .set(FIELD_RETRY_COUNT, document.retryCount)
            .set(FIELD_NEXT_RETRY_AT, document.nextRetryAt)
            .set(FIELD_LAST_ERROR, document.lastError)
            .set(FIELD_PUBLISHED_AT, document.publishedAt)
            .set(FIELD_CLAIMED_AT, null)
            .set(FIELD_CLAIMED_BY, null)
    }

    private suspend fun findNotification(outbox: NotificationOutbox) =
        mongoTemplate.findById(outbox.notificationId.id.toString(), NotificationDocument::class.java)
            .map(notificationMapper::toDomain)
            .awaitSingleOrNull()
            ?: throw IllegalStateException("notification not found. notificationId=${outbox.notificationId}")

    private suspend fun insertUncommittedHistories(notification: Notification) {
        notification.uncommittedHistories.forEach { history ->
            mongoTemplate.insert(notificationMapper.toHistoryDocument(history))
                .awaitSingle()
        }
    }

    private companion object {
        const val FIELD_ID = "_id"
        const val FIELD_OUTBOX_STATUS = "outboxStatus"
        const val FIELD_RETRY_COUNT = "retryCount"
        const val FIELD_NEXT_RETRY_AT = "nextRetryAt"
        const val FIELD_LAST_ERROR = "lastError"
        const val FIELD_PUBLISHED_AT = "publishedAt"
        const val FIELD_CLAIMED_AT = "claimedAt"
        const val FIELD_CLAIMED_BY = "claimedBy"
    }
}
