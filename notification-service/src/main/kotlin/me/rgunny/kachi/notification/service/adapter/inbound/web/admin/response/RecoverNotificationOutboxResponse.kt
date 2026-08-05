package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.dto.outbox.RecoverNotificationOutboxResult
import java.time.Instant

data class RecoverNotificationOutboxResponse(
    val outbox: NotificationOutboxAdminResponse,
    val recoveredAt: Instant,
) {
    companion object {
        fun from(result: RecoverNotificationOutboxResult): RecoverNotificationOutboxResponse {
            return RecoverNotificationOutboxResponse(
                outbox = NotificationOutboxAdminResponse.from(result.outbox),
                recoveredAt = result.recoveredAt,
            )
        }
    }
}
