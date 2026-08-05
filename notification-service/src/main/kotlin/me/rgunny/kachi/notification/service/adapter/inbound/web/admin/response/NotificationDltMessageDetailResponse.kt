package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageDetail
import java.time.Instant
import java.util.UUID

/**
 * 운영자가 DLT payload를 확인하고 재처리 가능 여부를 판단할 상세 응답.
 */
data class NotificationDltMessageDetailResponse(
    val messageId: UUID,
    val status: String,
    val originalTopic: String,
    val originalPartition: Int,
    val originalOffset: Long,
    val originalTimestamp: Instant?,
    val dltTopic: String,
    val dltPartition: Int,
    val dltOffset: Long,
    val consumerGroup: String?,
    val messageKey: String?,
    val payload: String,
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
        fun from(detail: NotificationDltMessageDetail): NotificationDltMessageDetailResponse {
            return NotificationDltMessageDetailResponse(
                messageId = detail.messageId.id,
                status = detail.status.name,
                originalTopic = detail.originalTopic,
                originalPartition = detail.originalPartition,
                originalOffset = detail.originalOffset,
                originalTimestamp = detail.originalTimestamp,
                dltTopic = detail.dltTopic,
                dltPartition = detail.dltPartition,
                dltOffset = detail.dltOffset,
                consumerGroup = detail.consumerGroup,
                messageKey = detail.messageKey,
                payload = detail.payload,
                exceptionFqcn = detail.exceptionFqcn,
                exceptionMessage = detail.exceptionMessage,
                deadLetteredAt = detail.deadLetteredAt,
                storedAt = detail.storedAt,
                discardedAt = detail.discardedAt,
                discardReason = detail.discardReason,
                reprocessedAt = detail.reprocessedAt,
                reprocessReason = detail.reprocessReason,
            )
        }
    }
}
