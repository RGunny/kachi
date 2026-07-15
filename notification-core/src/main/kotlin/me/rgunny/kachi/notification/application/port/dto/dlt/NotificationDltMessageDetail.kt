package me.rgunny.kachi.notification.application.port.dto.dlt

import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import java.time.Instant

/**
 * 운영자가 DLT 메시지 재처리 가능 여부를 판단할 상세 snapshot.
 *
 * 목록 응답에서 제외한 원본 payload를 포함한다.
 */
data class NotificationDltMessageDetail(
    val messageId: NotificationDltMessageId,
    val status: NotificationDltMessageStatus,
    val originalTopic: String,
    val originalPartition: Int,
    val originalOffset: Long,
    val dltTopic: String,
    val dltPartition: Int,
    val dltOffset: Long,
    val consumerGroup: String?,
    val messageKey: String?,
    val payload: String,
    val exceptionFqcn: String?,
    val exceptionMessage: String?,
    val failedAt: Instant,
    val receivedAt: Instant,
) {
    companion object {
        fun from(message: NotificationDltMessage): NotificationDltMessageDetail {
            return NotificationDltMessageDetail(
                messageId = message.id,
                status = message.status,
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
            )
        }
    }
}
