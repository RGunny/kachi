package me.rgunny.kachi.notification.application.port.outbound.dlt

import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus

/**
 * Notification dispatch DLT 메시지 운영 조회 port.
 */
interface NotificationDltMessageAdminPersistencePort {

    /**
     * 상태별 DLT 메시지를 실패 시각 최신순으로 조회한다.
     */
    suspend fun findByStatus(
        status: NotificationDltMessageStatus,
        batchSize: Int,
    ): List<NotificationDltMessage>

    /**
     * DLT 메시지를 식별자로 조회한다.
     */
    suspend fun findById(messageId: NotificationDltMessageId): NotificationDltMessage?

    /**
     * PENDING 상태일 때만 DISCARDED로 조건부 갱신한다.
     */
    suspend fun discardIfPending(message: NotificationDltMessage): NotificationDltMessage?
}
