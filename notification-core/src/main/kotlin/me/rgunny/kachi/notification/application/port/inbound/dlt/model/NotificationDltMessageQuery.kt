package me.rgunny.kachi.notification.application.port.inbound.dlt.model

import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus

/**
 * 운영자가 확인할 DLT 메시지 목록 조회 조건.
 */
data class NotificationDltMessageQuery(
    val status: NotificationDltMessageStatus = NotificationDltMessageStatus.PENDING,
    val batchSize: Int,
) {
    init {
        require(batchSize in 1..500) { "batchSize must be between 1 and 500" }
    }
}
