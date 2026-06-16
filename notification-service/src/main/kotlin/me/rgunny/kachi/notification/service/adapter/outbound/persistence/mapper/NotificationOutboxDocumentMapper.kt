package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper

import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxId
import me.rgunny.kachi.notification.domain.NotificationOutboxStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationOutboxDocument
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * NotificationOutbox domain <-> Mongo document 매퍼.
 *
 * eventPayload는 serializer port를 지난 문자열 payload이므로 mapper에서 JSON을 파싱하지 않는다.
 */
@Component
class NotificationOutboxDocumentMapper {

    fun toDocument(outbox: NotificationOutbox): NotificationOutboxDocument {
        return NotificationOutboxDocument(
            id = outbox.id.id.toString(),
            notificationId = outbox.notificationId.id.toString(),
            topic = outbox.topic,
            partitionKey = outbox.partitionKey,
            eventPayload = outbox.eventPayload,
            createdAt = outbox.createdAt,
            outboxStatus = outbox.outboxStatus.name,
            retryCount = outbox.retryCount,
            nextRetryAt = outbox.nextRetryAt,
            lastError = outbox.lastError,
            publishedAt = outbox.publishedAt,
            claimedAt = outbox.claimedAt,
            claimedBy = outbox.claimedBy,
        )
    }

    fun toDomain(document: NotificationOutboxDocument): NotificationOutbox {
        return NotificationOutbox.restore(
            id = NotificationOutboxId.of(UUID.fromString(document.id)),
            notificationId = NotificationId.of(UUID.fromString(document.notificationId)),
            topic = document.topic,
            partitionKey = document.partitionKey,
            eventPayload = document.eventPayload,
            createdAt = document.createdAt,
            outboxStatus = NotificationOutboxStatus.valueOf(document.outboxStatus),
            retryCount = document.retryCount,
            nextRetryAt = document.nextRetryAt,
            lastError = document.lastError,
            publishedAt = document.publishedAt,
            claimedAt = document.claimedAt,
            claimedBy = document.claimedBy,
        )
    }
}
