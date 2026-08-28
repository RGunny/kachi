package me.rgunny.kachi.notification.domain

/**
 * 알림이 어느 요약·키워드·사용자에서 비롯됐는지 나타내는 출처.
 *
 * 요청 계약에 출처가 실려 온 알림만 값을 가지며, 출처 없이 들어온 알림은 [NONE]이다.
 * 발송 이력을 사용자·요약 축으로 모으는 조회의 원천이고 발송 자체에는 관여하지 않는다.
 */
data class NotificationOrigin(
    val summaryId: String?,
    val keyword: String?,
    val userId: String?,
) {
    init {
        require(summaryId == null || summaryId.isNotBlank()) { "summaryId must not be blank" }
        require(keyword == null || keyword.isNotBlank()) { "keyword must not be blank" }
        require(userId == null || userId.isNotBlank()) { "userId must not be blank" }
    }

    companion object {
        val NONE = NotificationOrigin(summaryId = null, keyword = null, userId = null)
    }
}
