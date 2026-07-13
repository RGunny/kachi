package me.rgunny.kachi.notification.application.port.outbound

import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationOutbox

/**
 * Notification 운영 변경 저장 port.
 *
 * 구현체는 Notification 현재 상태 document 변경, 상태 전이 history insert, 새 outbox insert를
 * 하나의 DB transaction에서 확정해야 한다.
 */
interface NotificationAdminPersistencePort {

    /**
     * DEAD 상태일 때만 REQUESTED로 조건부 갱신하고, 상태 이력과 새 dispatch outbox를 함께 저장한다.
     */
    suspend fun recoverDeadToRequested(
        notification: Notification,
        outbox: NotificationOutbox,
    ): Notification?
}
