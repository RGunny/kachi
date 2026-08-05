package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.dto.admin.RecoverDeadNotificationResult
import java.time.Instant
import java.util.UUID

/**
 * DEAD notification 수동 복구 API 응답.
 */
data class RecoverDeadNotificationResponse(
    val notificationId: UUID,
    val status: String,
    val outboxId: UUID,
    val recoveredAt: Instant,
) {
    companion object {
        /**
         * core use case 결과를 HTTP response DTO로 변환한다.
         */
        fun from(result: RecoverDeadNotificationResult): RecoverDeadNotificationResponse {
            return RecoverDeadNotificationResponse(
                notificationId = result.notificationId.id,
                status = result.status.name,
                outboxId = result.outboxId.id,
                recoveredAt = result.recoveredAt,
            )
        }
    }
}
