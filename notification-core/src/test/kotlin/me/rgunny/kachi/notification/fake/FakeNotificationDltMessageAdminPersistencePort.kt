package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.NotificationDltMessageAdminPersistencePort
import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus

class FakeNotificationDltMessageAdminPersistencePort(
    private val messages: List<NotificationDltMessage> = emptyList(),
) : NotificationDltMessageAdminPersistencePort {
    var forceDiscardMismatch: Boolean = false
    var lastStatus: NotificationDltMessageStatus? = null
        private set
    var lastBatchSize: Int? = null
        private set

    override suspend fun findByStatus(
        status: NotificationDltMessageStatus,
        batchSize: Int,
    ): List<NotificationDltMessage> {
        lastStatus = status
        lastBatchSize = batchSize
        return messages
            .filter { it.status == status }
            .sortedByDescending { it.deadLetteredAt }
            .take(batchSize)
    }

    override suspend fun findById(messageId: NotificationDltMessageId): NotificationDltMessage? {
        return messages.firstOrNull { it.id == messageId }
    }

    override suspend fun discardIfPending(message: NotificationDltMessage): NotificationDltMessage? {
        if (forceDiscardMismatch || message.status != NotificationDltMessageStatus.DISCARDED) {
            return null
        }
        return message
    }
}
