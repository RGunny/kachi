package me.rgunny.kachi.notification.contract

/**
 * notification-service가 notification-worker로 발송 실행을 요청할 때 사용하는 Kafka 계약.
 */
data class NotificationDispatchEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val notificationId: String,
    val requestId: String,
    val channel: NotificationChannel,
    val recipient: String,
    val message: String,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
