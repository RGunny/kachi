package me.rgunny.kachi.notification.contract

/**
 * 외부 bounded context가 notification-service로 알림 요청을 전달할 때 사용하는 Kafka 계약.
 *
 * [recipient]는 주소가 아니라 주소를 가리키는 참조(채널 바인딩 id)다. 주소 해석은 발송 쪽이 한다.
 * 필드명은 초기 계약과의 호환을 위해 유지한다.
 */
data class NotificationRequestedEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val requestId: String,
    val requester: String,
    val channel: NotificationChannel,
    val recipient: String,
    val message: String,
    val origin: NotificationRequestedOrigin? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
