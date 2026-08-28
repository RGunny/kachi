package me.rgunny.kachi.notification.contract

/**
 * notification-service가 notification-worker로 발송 실행을 요청할 때 사용하는 Kafka 계약.
 *
 * [recipientId]는 수신자 식별자(user-service 사용자 id)이며 주소가 아니다. 수신 주소는 worker가 `(recipientId, channel)`로 받는다.
 * 이 필드는 `recipient`에서 이름을 바꿨다. 운영 전이라 [schemaVersion]은 1을 유지한다.
 */
data class NotificationDispatchEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val notificationId: String,
    val requestId: String,
    val channel: NotificationChannel,
    val recipientId: String,
    val message: String,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
