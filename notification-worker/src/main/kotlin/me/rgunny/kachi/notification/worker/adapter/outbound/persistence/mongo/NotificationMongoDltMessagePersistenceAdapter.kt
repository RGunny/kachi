package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mongo

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.application.port.outbound.NotificationDltMessagePersistencePort
import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mapper.NotificationDltMessageDocumentMapper
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
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
        // NotificationDltMessageId가 원본 topic/partition/offset 기반 deterministic id라 save가 upsert처럼 동작한다.
        val document = mapper.toDocument(message)
        return mongoTemplate.save(document)
            .map(mapper::toDomain)
            .awaitSingle()
    }
}
