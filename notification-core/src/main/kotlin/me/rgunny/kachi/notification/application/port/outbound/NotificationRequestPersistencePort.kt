package me.rgunny.kachi.notification.application.port.outbound

import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationOutbox

/**
 * 알림 요청 접수 저장 port.
 *
 * Notification과 dispatch outbox는 함께 확정되어야 한다.
 * 구현체는 둘 중 하나만 저장되는 상태가 남지 않도록 하나의 DB transaction 또는 그에 준하는 원자적 저장 경계를 제공해야 한다.
 */
interface NotificationRequestPersistencePort {

    suspend fun saveRequested(
        notification: Notification,
        outbox: NotificationOutbox,
    ): Notification
}
