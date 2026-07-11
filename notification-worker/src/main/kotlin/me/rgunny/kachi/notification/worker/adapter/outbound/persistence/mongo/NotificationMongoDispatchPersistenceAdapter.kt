package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mongo

import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.notification.application.port.outbound.NotificationDispatchPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.document.NotificationDocument
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * 외부 채널 발송 결과 저장 MongoDB 구현.
 *
 * 이 adapter가 worker dispatch 흐름의 DB finalize 경계다.
 *
 * 전체 dispatch 흐름은 다음처럼 분리한다.
 *
 * 1. Redis dedupe가 같은 Kafka message의 동시 처리를 1차로 막는다.
 * 2. NotificationPersistencePort.claimFromPublished/claimFromRetryWait가 PROCESSING 상태를 CAS로 선점한다.
 * 3. Redis idempotency key를 조회/생성하고 vendor API를 호출한다.
 * 4. vendor 호출 결과만 이 adapter가 MongoDB에 최종 저장한다.
 *
 * MongoDB transaction은 Redis dedupe, Redis idempotency key, vendor HTTP 호출을 rollback하지 못한다.
 * 현재 Notification은 단일 document aggregate이므로 최종 상태 저장은 MongoDB 단일 document 원자 write로 충분하다.
 * 나중에 dispatch outbox/history 별도 collection처럼 함께 확정해야 하는 document가 생기면 이 adapter가 transaction 경계가 된다.
 */
@Repository
class NotificationMongoDispatchPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val mapper: NotificationDocumentMapper,
) : NotificationDispatchPersistencePort {

    /**
     * vendor 호출 이후 core가 확정한 SENT/RETRY_WAIT/DEAD 상태를 조건부 저장한다.
     *
     * `_id + PROCESSING + claimedAt + claimedBy`가 맞지 않으면 이미 다른 worker/recovery가 상태를 확정했거나
     * 소유권이 바뀐 stale 결과이므로 null을 반환한다.
     *
     * 조건이 맞으면 core가 계산한 최종 상태와 history를 반영하고, PROCESSING claim 정보는 finalize와 함께 제거한다.
     */
    override suspend fun saveFinalizedIfProcessingClaimMatches(
        notification: Notification,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
    ): Notification? {
        require(expectedClaimedBy.isNotBlank()) { "expectedClaimedBy must not be blank" }

        // 1. 외부 vendor 호출 이후 finalize는 caller가 획득했던 PROCESSING claim이 아직 유효할 때만 저장한다.
        // 조건 불일치는 저장소 장애가 아니라 stale owner 결과이므로 null을 반환한다.
        return mongoTemplate.findAndModify(
            processingClaimQuery(
                notification = notification,
                expectedClaimedAt = expectedClaimedAt,
                expectedClaimedBy = expectedClaimedBy,
            ),
            // 2. 조건이 맞으면 최종 상태와 history를 반영하고 PROCESSING claim 정보를 제거한다.
            finalizedUpdate(notification),
            FindAndModifyOptions.options().returnNew(true),
            NotificationDocument::class.java,
        )
            .map(mapper::toDomain)
            .awaitSingleOrNull()
    }

    private fun processingClaimQuery(
        notification: Notification,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
    ): Query {
        return Query.query(
            Criteria.where(FIELD_ID).`is`(notification.id.id.toString())
                .and(FIELD_STATUS).`is`(NotificationStatus.PROCESSING.name)
                .and(FIELD_CLAIMED_AT).`is`(expectedClaimedAt)
                .and(FIELD_CLAIMED_BY).`is`(expectedClaimedBy)
        )
    }

    private fun finalizedUpdate(notification: Notification): Update {
        val document = mapper.toDocument(notification)

        return Update()
            .set(FIELD_STATUS, document.status)
            .set(FIELD_FAILURE_REASON, document.failureReason)
            .set(FIELD_UPDATED_AT, document.updatedAt)
            .set(FIELD_LAST_TRANSITION_AT, document.lastTransitionAt)
            .set(FIELD_DISPATCH_ATTEMPTS, document.dispatchAttempts)
            .set(FIELD_CLAIMED_AT, null)
            .set(FIELD_CLAIMED_BY, null)
            .set(FIELD_HISTORIES, document.histories)
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
        const val FIELD_HISTORIES = "histories"
    }
}
