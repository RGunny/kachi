package me.rgunny.kachi.notification.routing.application.port.outbound.messaging.model

/**
 * 알림 요청의 출처.
 * 요약 알림은 셋 다, 관리자 알림은 keyword만 가진다.
 */
data class NotificationRequestOrigin(
    val summaryId: String?,
    val keyword: String?,
    val userId: String?,
) {
    init {
        require(summaryId == null || summaryId.isNotBlank()) { "summaryId must not be blank" }
        require(keyword == null || keyword.isNotBlank()) { "keyword must not be blank" }
        require(userId == null || userId.isNotBlank()) { "userId must not be blank" }
    }
}
