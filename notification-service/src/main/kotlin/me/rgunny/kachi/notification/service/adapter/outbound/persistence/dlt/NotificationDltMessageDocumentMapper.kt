package me.rgunny.kachi.notification.service.adapter.outbound.persistence.dlt

import java.util.UUID
import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.dlt.NotificationDltMessageDocument
import org.springframework.stereotype.Component

/**
 * NotificationDltMessage domain <-> service Mongo document mapper.
 */
@Component
class NotificationDltMessageDocumentMapper {

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
