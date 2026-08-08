package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.inbound.dlt.model.DiscardNotificationDltMessageResult
import java.time.Instant
import java.util.UUID

/**
 * DLT 메시지 폐기 처리 응답.
 */
data class DiscardNotificationDltMessageResponse(
    val messageId: UUID,
    val status: String,
    val discardedAt: Instant,
    val discardReason: String,
) {
    companion object {
        fun from(result: DiscardNotificationDltMessageResult): DiscardNotificationDltMessageResponse {
            return DiscardNotificationDltMessageResponse(
                messageId = result.messageId.id,
                status = result.status.name,
                discardedAt = result.discardedAt,
                discardReason = result.discardReason,
            )
        }
    }
}
