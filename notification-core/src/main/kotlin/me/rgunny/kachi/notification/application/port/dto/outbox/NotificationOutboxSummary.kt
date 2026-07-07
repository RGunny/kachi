package me.rgunny.kachi.notification.application.port.dto.outbox

import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxId
import me.rgunny.kachi.notification.domain.NotificationOutboxStatus
import java.time.Instant

/**
 * 운영 화면/API에 노출할 outbox snapshot.
 *
 * eventPayload는 메시지 본문을 포함할 수 있으므로 목록 응답에서는 제외한다.
 */
data class NotificationOutboxSummary(
    val outboxId: NotificationOutboxId,
    val notificationId: NotificationId,
    val topic: String,
    val partitionKey: String,
    val status: NotificationOutboxStatus,
    val retryCount: Int,
    val nextRetryAt: Instant,
    val lastError: String?,
    val createdAt: Instant,
    val publishedAt: Instant?,
) {
    companion object {
        fun from(outbox: NotificationOutbox): NotificationOutboxSummary {
            return NotificationOutboxSummary(
                outboxId = outbox.id,
                notificationId = outbox.notificationId,
                topic = outbox.topic,
                partitionKey = outbox.partitionKey,
                status = outbox.outboxStatus,
                retryCount = outbox.retryCount,
                nextRetryAt = outbox.nextRetryAt,
                lastError = outbox.lastError,
                createdAt = outbox.createdAt,
                publishedAt = outbox.publishedAt,
            )
        }
    }
}
