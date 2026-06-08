package me.rgunny.kachi.notification.domain

import java.time.Instant

/**
 * 알림 상태 전이에 따른 append-only 히스토리 로그
 */
class NotificationHistory private constructor(

    val id: NotificationHistoryId,
    val notificationId: NotificationId,
    val fromStatus: NotificationStatus,
    val toStatus: NotificationStatus,
    var reason: String? = null,
    var createdAt: Instant? = null
){

    companion object {

        fun record(
            notificationId: NotificationId,
            fromStatus: NotificationStatus,
            toStatus: NotificationStatus,
            createdAt: Instant,
            reason: String?,
        ): NotificationHistory {
            return NotificationHistory(
                id = NotificationHistoryId.newId(),
                notificationId = notificationId,
                fromStatus = fromStatus,
                toStatus = toStatus,
                createdAt = createdAt,
                reason = reason,
            )
        }
    }

}
