package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper

import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationHistory
import me.rgunny.kachi.notification.domain.NotificationHistoryId
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationHistoryDocument
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Notification domain <-> Mongo document 매퍼.
 */
@Component
class NotificationDocumentMapper {

    fun toDocument(notification: Notification): NotificationDocument {
        return NotificationDocument(
            id = notification.id.id.toString(),
            requestId = notification.requestId,
            requester = notification.requester,
            channel = notification.channel.name,
            recipient = notification.recipient,
            message = notification.message,
            requestedAt = notification.requestedAt,
            status = notification.status.name,
            failureReason = notification.failureReason,
            updatedAt = notification.updatedAt,
            lastTransitionAt = notification.lastTransitionAt,
            dispatchAttempts = notification.dispatchAttempts,
            claimedAt = notification.claimedAt,
            claimedBy = notification.claimedBy,
            histories = notification.histories.map(::toHistoryDocument),
        )
    }

    fun toDomain(document: NotificationDocument): Notification {
        return Notification.restore(
            id = NotificationId.of(UUID.fromString(document.id)),
            requestId = document.requestId,
            requester = document.requester,
            channel = NotificationChannel.valueOf(document.channel),
            recipient = document.recipient,
            message = document.message,
            requestedAt = document.requestedAt,
            status = NotificationStatus.valueOf(document.status),
            failureReason = document.failureReason,
            updatedAt = document.updatedAt,
            lastTransitionAt = document.lastTransitionAt,
            dispatchAttempts = document.dispatchAttempts,
            claimedAt = document.claimedAt,
            claimedBy = document.claimedBy,
            histories = document.histories.map(::toHistoryDomain),
        )
    }

    private fun toHistoryDocument(history: NotificationHistory): NotificationHistoryDocument {
        return NotificationHistoryDocument(
            id = history.id.id.toString(),
            notificationId = history.notificationId.id.toString(),
            fromStatus = history.fromStatus.name,
            toStatus = history.toStatus.name,
            reason = history.reason,
            createdAt = history.createdAt,
        )
    }

    private fun toHistoryDomain(document: NotificationHistoryDocument): NotificationHistory {
        return NotificationHistory.restore(
            id = NotificationHistoryId.of(UUID.fromString(document.id)),
            notificationId = NotificationId.of(UUID.fromString(document.notificationId)),
            fromStatus = NotificationStatus.valueOf(document.fromStatus),
            toStatus = NotificationStatus.valueOf(document.toStatus),
            createdAt = document.createdAt,
            reason = document.reason,
        )
    }
}
