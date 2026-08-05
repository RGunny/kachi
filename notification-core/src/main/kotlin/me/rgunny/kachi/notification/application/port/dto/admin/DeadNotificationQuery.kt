package me.rgunny.kachi.notification.application.port.dto.admin

/**
 * 운영자가 확인할 DEAD notification 목록 조회 조건.
 */
data class DeadNotificationQuery(
    val batchSize: Int,
) {
    init {
        require(batchSize in 1..500) { "batchSize must be between 1 and 500" }
    }
}
