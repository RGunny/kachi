package me.rgunny.kachi.notification.contract

/**
 * 알림 요청이 어느 요약·키워드·사용자에서 비롯됐는지 나타내는 출처.
 *
 * 라우팅이 만든 요청만 값을 채우고, 그 밖의 producer는 생략한다. 세 값 모두 선택이며 주소는 싣지 않는다.
 */
data class NotificationRequestedOrigin(
    val summaryId: String? = null,
    val keyword: String? = null,
    val userId: String? = null,
)
