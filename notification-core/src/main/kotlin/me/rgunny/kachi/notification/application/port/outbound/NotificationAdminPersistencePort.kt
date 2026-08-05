package me.rgunny.kachi.notification.application.port.outbound

import me.rgunny.kachi.notification.domain.NotificationHistory
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationOutbox

/**
 * Notification 운영 조회/변경 저장 port.
 *
 * 구현체는 Notification 현재 상태 document 변경, 상태 전이 history insert, 새 outbox insert를
 * 하나의 DB transaction에서 확정해야 한다.
 */
interface NotificationAdminPersistencePort {

    /**
     * 최신 변경 시각 기준으로 DEAD notification을 조회한다.
     */
    suspend fun findDead(batchSize: Int): List<Notification>

    /**
     * notification 상태 전이 history를 생성 시각 오름차순으로 조회한다.
     */
    suspend fun findHistories(notificationId: NotificationId, batchSize: Int): List<NotificationHistory>

    /**
     * DEAD 상태일 때만 REQUESTED로 조건부 갱신하고, 상태 이력과 새 dispatch outbox를 함께 저장한다.
     */
    suspend fun recoverDeadToRequested(
        notification: Notification,
        outbox: NotificationOutbox,
    ): Notification?
}
