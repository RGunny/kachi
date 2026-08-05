package me.rgunny.kachi.notification.application.port.dto.dlt

import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import java.time.Instant

/**
 * 운영 목록에 노출할 DLT 메시지 snapshot.
 *
 * payload는 길거나 민감 정보를 포함할 수 있어 목록 summary에서는 제외한다.
 */
data class NotificationDltMessageSummary(
    val messageId: NotificationDltMessageId,
    val status: NotificationDltMessageStatus,
    val originalTopic: String,
    val originalPartition: Int,
    val originalOffset: Long,
    val originalTimestamp: Instant?,
    val dltTopic: String,
    val dltPartition: Int,
    val dltOffset: Long,
    val consumerGroup: String?,
    val messageKey: String?,
    val exceptionFqcn: String?,
    val exceptionMessage: String?,
    val deadLetteredAt: Instant,
    val storedAt: Instant,
    val discardedAt: Instant?,
    val discardReason: String?,
    val reprocessedAt: Instant?,
    val reprocessReason: String?,
) {
    companion object {
        fun from(message: NotificationDltMessage): NotificationDltMessageSummary {
            return NotificationDltMessageSummary(
                messageId = message.id,
                status = message.status,
                originalTopic = message.originalTopic,
                originalPartition = message.originalPartition,
                originalOffset = message.originalOffset,
                originalTimestamp = message.originalTimestamp,
                dltTopic = message.dltTopic,
                dltPartition = message.dltPartition,
                dltOffset = message.dltOffset,
                consumerGroup = message.consumerGroup,
                messageKey = message.messageKey,
                exceptionFqcn = message.exceptionFqcn,
                exceptionMessage = message.exceptionMessage,
                deadLetteredAt = message.deadLetteredAt,
                storedAt = message.storedAt,
                discardedAt = message.discardedAt,
                discardReason = message.discardReason,
                reprocessedAt = message.reprocessedAt,
                reprocessReason = message.reprocessReason,
            )
        }
    }
}
