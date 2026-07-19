package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.notification.application.port.outbound.NotificationDltMessageAdminPersistencePort
import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationDltMessageDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationDltMessageDocumentMapper
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

/**
 * notification dispatch DLT 메시지 운영 조회 MongoDB 구현.
 */
@Repository
class NotificationMongoDltMessageAdminPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val mapper: NotificationDltMessageDocumentMapper,
) : NotificationDltMessageAdminPersistencePort {

    /**
     * 운영 목록은 처리되지 않은 최신 실패를 먼저 볼 수 있도록 deadLetteredAt 내림차순으로 조회한다.
     */
    override suspend fun findByStatus(
        status: NotificationDltMessageStatus,
        batchSize: Int,
    ): List<NotificationDltMessage> {
        return mongoTemplate.find(
            Query.query(Criteria.where(FIELD_STATUS).`is`(status.name))
                .with(Sort.by(Sort.Direction.DESC, FIELD_DEAD_LETTERED_AT))
                .limit(batchSize),
            NotificationDltMessageDocument::class.java,
        )
            .map(mapper::toDomain)
            .collectList()
            .awaitSingle()
    }

    /**
     * 운영자가 선택한 DLT 메시지 상세를 식별자로 조회한다.
     */
    override suspend fun findById(messageId: NotificationDltMessageId): NotificationDltMessage? {
        return mongoTemplate.findById(messageId.id.toString(), NotificationDltMessageDocument::class.java)
            .map(mapper::toDomain)
            .awaitSingleOrNull()
    }

    /**
     * PENDING 조건이 맞는 경우에만 DLT 메시지를 DISCARDED로 조건부 갱신한다.
     */
    override suspend fun discardIfPending(message: NotificationDltMessage): NotificationDltMessage? {
        // 1. 조회 이후 다른 운영자가 먼저 처리했을 수 있으므로 id와 현재 상태를 함께 조건으로 건다.
        return mongoTemplate.findAndModify(
            Query.query(
                Criteria.where(FIELD_ID).`is`(message.id.id.toString())
                    .and(FIELD_STATUS).`is`(NotificationDltMessageStatus.PENDING.name)
            ),
            Update()
                .set(FIELD_STATUS, message.status.name)
                .set(FIELD_DISCARDED_AT, message.discardedAt)
                .set(FIELD_DISCARD_REASON, message.discardReason),
            FindAndModifyOptions.options().returnNew(true), // update 이후 document 반환
            NotificationDltMessageDocument::class.java,
        )
            // 2. 조건 불일치면 null을 반환해 caller가 stale 운영 요청으로 판단하게 한다.
            .map(mapper::toDomain)
            .awaitSingleOrNull()
    }

    private companion object {
        const val FIELD_ID = "_id"
        const val FIELD_STATUS = "status"
        const val FIELD_DEAD_LETTERED_AT = "deadLetteredAt"
        const val FIELD_DISCARDED_AT = "discardedAt"
        const val FIELD_DISCARD_REASON = "discardReason"
    }
}
