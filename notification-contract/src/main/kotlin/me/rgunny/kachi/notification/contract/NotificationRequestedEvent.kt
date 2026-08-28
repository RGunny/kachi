package me.rgunny.kachi.notification.contract

/**
 * 외부 bounded context가 notification-service로 알림 요청을 전달할 때 사용하는 Kafka 계약.
 *
 * [recipientId]는 수신자 식별자(user-service 사용자 id)이며 주소가 아니다.
 * 수신 주소는 발송 쪽이 `(recipientId, channel)`로 user-service에서 받는다.
 * 이 필드는 `recipient`에서 이름을 바꿨다. 운영 전이고 producer가 notification-routing뿐이라 [schemaVersion]은 1을 유지한다.
 */
data class NotificationRequestedEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val requestId: String,
    val requester: String,
    val channel: NotificationChannel,
    val recipientId: String,
    val message: String,
    val origin: NotificationRequestedOrigin? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
