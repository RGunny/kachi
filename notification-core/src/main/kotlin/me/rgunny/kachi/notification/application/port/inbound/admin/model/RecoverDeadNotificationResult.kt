package me.rgunny.kachi.notification.application.port.inbound.admin.model

import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutboxId
import me.rgunny.kachi.notification.domain.NotificationStatus
import java.time.Instant

/**
 * DEAD notification 수동 복구 결과와 새로 생성된 dispatch outbox 식별자.
 */
data class RecoverDeadNotificationResult(
    val notificationId: NotificationId,
    val status: NotificationStatus,
    val outboxId: NotificationOutboxId,
    val recoveredAt: Instant,
)
