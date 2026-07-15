package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageSummary
import java.time.Instant
import java.util.UUID

/**
 * 운영자가 DLT 원인과 원본 Kafka 위치를 확인할 목록 응답.
 */
data class NotificationDltMessageAdminResponse(
    val messageId: UUID,
    val status: String,
    val originalTopic: String,
    val originalPartition: Int,
    val originalOffset: Long,
    val dltTopic: String,
    val dltPartition: Int,
    val dltOffset: Long,
    val consumerGroup: String?,
    val messageKey: String?,
    val exceptionFqcn: String?,
    val exceptionMessage: String?,
    val failedAt: Instant,
    val receivedAt: Instant,
) {
    companion object {
        fun from(summary: NotificationDltMessageSummary): NotificationDltMessageAdminResponse {
            return NotificationDltMessageAdminResponse(
                messageId = summary.messageId.id,
                status = summary.status.name,
                originalTopic = summary.originalTopic,
                originalPartition = summary.originalPartition,
                originalOffset = summary.originalOffset,
                dltTopic = summary.dltTopic,
                dltPartition = summary.dltPartition,
                dltOffset = summary.dltOffset,
                consumerGroup = summary.consumerGroup,
                messageKey = summary.messageKey,
                exceptionFqcn = summary.exceptionFqcn,
                exceptionMessage = summary.exceptionMessage,
                failedAt = summary.failedAt,
                receivedAt = summary.receivedAt,
            )
        }
    }
}
