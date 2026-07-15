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
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
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
     * 운영 목록은 처리되지 않은 최신 실패를 먼저 볼 수 있도록 failedAt 내림차순으로 조회한다.
     */
    override suspend fun findByStatus(
        status: NotificationDltMessageStatus,
        batchSize: Int,
    ): List<NotificationDltMessage> {
        return mongoTemplate.find(
            Query.query(Criteria.where(FIELD_STATUS).`is`(status.name))
                .with(Sort.by(Sort.Direction.DESC, FIELD_FAILED_AT))
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

    private companion object {
        const val FIELD_STATUS = "status"
        const val FIELD_FAILED_AT = "failedAt"
    }
}
