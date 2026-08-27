package me.rgunny.kachi.notification.routing.adapter.outbound.subscriber

/**
 * user-service internal API 응답 envelope.
 * 실패 응답의 error 본문은 쓰지 않으므로 받지 않는다.
 */
data class UserServiceApiResponse<T>(
    val success: Boolean,
    val data: T?,
)

/**
 * 구독 조회 응답 한 건.
 * channel은 user-service의 enum 이름이며 어댑터가 core 채널로 짝짓는다.
 */
data class UserServiceSubscriberResponse(
    val userId: String,
    val channel: String,
    val recipientRef: String,
)
