package me.rgunny.kachi.notification.application.port.dto.admin

import me.rgunny.kachi.notification.domain.NotificationId

/**
 * 특정 notification의 상태 전이 history 조회 조건.
 */
data class NotificationHistoryQuery(
    val notificationId: NotificationId,
    val batchSize: Int,
) {
    init {
        require(batchSize in 1..500) { "batchSize must be between 1 and 500" }
    }
}
