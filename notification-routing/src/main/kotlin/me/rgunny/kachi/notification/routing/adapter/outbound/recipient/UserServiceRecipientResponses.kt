package me.rgunny.kachi.notification.routing.adapter.outbound.recipient

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
 * channel은 user-service의 enum 이름이며 어댑터가 계약 채널로 짝짓는다.
 */
data class UserServiceSubscriberResponse(
    val userId: String,
    val channel: String,
)

/**
 * 역할별 수신자 조회 응답 한 건. channels는 그 사용자가 받을 수 있는 채널이다.
 */
data class UserServiceUserChannelsResponse(
    val userId: String,
    val channels: List<String>,
)
