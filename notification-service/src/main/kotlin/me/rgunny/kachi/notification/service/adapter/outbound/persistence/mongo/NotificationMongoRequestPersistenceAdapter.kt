package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.application.port.outbound.NotificationRequestPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationOutboxDocumentMapper
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

/**
 * 알림 요청 접수 저장 MongoDB 구현.
 *
 * 요청 접수는 Notification 저장과 dispatch outbox 저장이 함께 확정되어야 한다.
 * MongoDB transaction은 MongoDB write만 rollback할 수 있으므로 Redis dedupe, Kafka publish, 외부 API 호출은 이 경계 밖에서 처리한다.
 */
@Repository
class NotificationMongoRequestPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val notificationMapper: NotificationDocumentMapper,
    private val outboxMapper: NotificationOutboxDocumentMapper,
    private val transactionalOperator: TransactionalOperator,
) : NotificationRequestPersistencePort {

    override suspend fun saveRequested(
        notification: Notification,
        outbox: NotificationOutbox,
    ): Notification {
        return transactionalOperator.executeAndAwait {
            val savedNotification = mongoTemplate.save(notificationMapper.toDocument(notification))
                .map(notificationMapper::toDomain)
                .awaitSingle()

            mongoTemplate.save(outboxMapper.toDocument(outbox))
                .awaitSingle()

            return@executeAndAwait savedNotification
        }
    }
}
