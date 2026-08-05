package me.rgunny.kachi.notification.contract

/**
 * 외부 bounded context가 notification-service로 알림 요청을 전달할 때 사용하는 Kafka 계약.
 */
data class NotificationRequestedEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val requestId: String,
    val requester: String,
    val channel: NotificationChannel,
    val recipient: String,
    val message: String,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
