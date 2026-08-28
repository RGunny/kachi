package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

/**
 * 채널 바인딩 조회 응답. channel·status는 user-service의 enum 이름이고 address는 ACTIVE일 때만 온다.
 */
data class UserServiceChannelBindingResponse(
    val channel: String,
    val status: String,
    val address: String?,
)
