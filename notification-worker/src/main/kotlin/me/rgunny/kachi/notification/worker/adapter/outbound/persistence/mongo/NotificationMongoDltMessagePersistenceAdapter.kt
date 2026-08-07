package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mongo

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationDltMessagePersistencePort
import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.document.NotificationDltMessageDocument
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mapper.NotificationDltMessageDocumentMapper
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

/**
 * notification dispatch DLT 메시지 MongoDB 저장 구현.
 */
@Repository
class NotificationMongoDltMessagePersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val mapper: NotificationDltMessageDocumentMapper,
) : NotificationDltMessagePersistencePort {

    /**
     * 원본 Kafka record 위치 기준으로 upsert해 DLT consumer 재처리 중복 저장을 막는다.
     */
    override suspend fun save(message: NotificationDltMessage): NotificationDltMessage {
        val document = mapper.toDocument(message)
        return mongoTemplate.findAndModify(
            Query.query(Criteria.where(FIELD_ID).`is`(document.id)),
            Update()
                .setOnInsert(FIELD_ID, document.id)
                .setOnInsert(FIELD_ORIGINAL_TOPIC, document.originalTopic)
                .setOnInsert(FIELD_ORIGINAL_PARTITION, document.originalPartition)
                .setOnInsert(FIELD_ORIGINAL_OFFSET, document.originalOffset)
                .setOnInsert(FIELD_ORIGINAL_TIMESTAMP, document.originalTimestamp)
                .setOnInsert(FIELD_DLT_TOPIC, document.dltTopic)
                .setOnInsert(FIELD_DLT_PARTITION, document.dltPartition)
                .setOnInsert(FIELD_DLT_OFFSET, document.dltOffset)
                .setOnInsert(FIELD_CONSUMER_GROUP, document.consumerGroup)
                .setOnInsert(FIELD_MESSAGE_KEY, document.messageKey)
                .setOnInsert(FIELD_PAYLOAD, document.payload)
                .setOnInsert(FIELD_EXCEPTION_FQCN, document.exceptionFqcn)
                .setOnInsert(FIELD_EXCEPTION_MESSAGE, document.exceptionMessage)
                .setOnInsert(FIELD_DEAD_LETTERED_AT, document.deadLetteredAt)
                .setOnInsert(FIELD_STORED_AT, document.storedAt)
                .setOnInsert(FIELD_DISCARDED_AT, document.discardedAt)
                .setOnInsert(FIELD_DISCARD_REASON, document.discardReason)
                .setOnInsert(FIELD_REPROCESSED_AT, document.reprocessedAt)
                .setOnInsert(FIELD_REPROCESS_REASON, document.reprocessReason)
                .setOnInsert(FIELD_STATUS, document.status),
            FindAndModifyOptions.options().upsert(true).returnNew(true),
            NotificationDltMessageDocument::class.java,
        )
            .map(mapper::toDomain)
            .awaitSingle()
    }

    private companion object {
        const val FIELD_ID = "_id"
        const val FIELD_ORIGINAL_TOPIC = "originalTopic"
        const val FIELD_ORIGINAL_PARTITION = "originalPartition"
        const val FIELD_ORIGINAL_OFFSET = "originalOffset"
        const val FIELD_ORIGINAL_TIMESTAMP = "originalTimestamp"
        const val FIELD_DLT_TOPIC = "dltTopic"
        const val FIELD_DLT_PARTITION = "dltPartition"
        const val FIELD_DLT_OFFSET = "dltOffset"
        const val FIELD_CONSUMER_GROUP = "consumerGroup"
        const val FIELD_MESSAGE_KEY = "messageKey"
        const val FIELD_PAYLOAD = "payload"
        const val FIELD_EXCEPTION_FQCN = "exceptionFqcn"
        const val FIELD_EXCEPTION_MESSAGE = "exceptionMessage"
        const val FIELD_DEAD_LETTERED_AT = "deadLetteredAt"
        const val FIELD_STORED_AT = "storedAt"
        const val FIELD_DISCARDED_AT = "discardedAt"
        const val FIELD_DISCARD_REASON = "discardReason"
        const val FIELD_REPROCESSED_AT = "reprocessedAt"
        const val FIELD_REPROCESS_REASON = "reprocessReason"
        const val FIELD_STATUS = "status"
    }
}
