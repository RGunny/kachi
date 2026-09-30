package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.dlt

import java.util.UUID
import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.dlt.NotificationDltMessageDocument
import org.springframework.stereotype.Component

/**
 * NotificationDltMessage domain <-> worker Mongo document mapper.
 */
@Component
class NotificationDltMessageDocumentMapper {

    fun toDocument(message: NotificationDltMessage): NotificationDltMessageDocument {
        return NotificationDltMessageDocument(
            id = message.id.id.toString(),
            originalTopic = message.originalTopic,
            originalPartition = message.originalPartition,
            originalOffset = message.originalOffset,
            originalTimestamp = message.originalTimestamp,
            dltTopic = message.dltTopic,
            dltPartition = message.dltPartition,
            dltOffset = message.dltOffset,
            consumerGroup = message.consumerGroup,
            messageKey = message.messageKey,
            payload = message.payload,
            exceptionFqcn = message.exceptionFqcn,
            exceptionMessage = message.exceptionMessage,
            deadLetteredAt = message.deadLetteredAt,
            storedAt = message.storedAt,
            discardedAt = message.discardedAt,
            discardReason = message.discardReason,
            reprocessedAt = message.reprocessedAt,
            reprocessReason = message.reprocessReason,
            status = message.status.name,
        )
    }

    fun toDomain(document: NotificationDltMessageDocument): NotificationDltMessage {
        return NotificationDltMessage.restore(
            id = NotificationDltMessageId.of(UUID.fromString(document.id)),
            originalTopic = document.originalTopic,
            originalPartition = document.originalPartition,
            originalOffset = document.originalOffset,
            originalTimestamp = document.originalTimestamp,
            dltTopic = document.dltTopic,
            dltPartition = document.dltPartition,
            dltOffset = document.dltOffset,
            consumerGroup = document.consumerGroup,
            messageKey = document.messageKey,
            payload = document.payload,
            exceptionFqcn = document.exceptionFqcn,
            exceptionMessage = document.exceptionMessage,
            deadLetteredAt = document.deadLetteredAt,
            storedAt = document.storedAt,
            discardedAt = document.discardedAt,
            discardReason = document.discardReason,
            reprocessedAt = document.reprocessedAt,
            reprocessReason = document.reprocessReason,
            status = NotificationDltMessageStatus.valueOf(document.status),
        )
    }
}
