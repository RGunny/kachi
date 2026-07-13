package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mapper

import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.document.NotificationDltMessageDocument
import org.springframework.stereotype.Component
import java.util.UUID

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
            dltTopic = message.dltTopic,
            dltPartition = message.dltPartition,
            dltOffset = message.dltOffset,
            consumerGroup = message.consumerGroup,
            messageKey = message.messageKey,
            payload = message.payload,
            exceptionFqcn = message.exceptionFqcn,
            exceptionMessage = message.exceptionMessage,
            failedAt = message.failedAt,
            receivedAt = message.receivedAt,
            status = message.status.name,
        )
    }

    fun toDomain(document: NotificationDltMessageDocument): NotificationDltMessage {
        return NotificationDltMessage.restore(
            id = NotificationDltMessageId.of(UUID.fromString(document.id)),
            originalTopic = document.originalTopic,
            originalPartition = document.originalPartition,
            originalOffset = document.originalOffset,
            dltTopic = document.dltTopic,
            dltPartition = document.dltPartition,
            dltOffset = document.dltOffset,
            consumerGroup = document.consumerGroup,
            messageKey = document.messageKey,
            payload = document.payload,
            exceptionFqcn = document.exceptionFqcn,
            exceptionMessage = document.exceptionMessage,
            failedAt = document.failedAt,
            receivedAt = document.receivedAt,
            status = NotificationDltMessageStatus.valueOf(document.status),
        )
    }
}
