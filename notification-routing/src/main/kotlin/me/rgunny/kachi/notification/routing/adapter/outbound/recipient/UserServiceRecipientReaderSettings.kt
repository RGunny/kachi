package me.rgunny.kachi.notification.routing.adapter.outbound.recipient

import java.time.Duration

/**
 * user-service 수신자 조회 클라이언트가 보는 호출 설정.
 *
 * `kachi.notification.routing.user-service`
 */
data class UserServiceRecipientReaderSettings(
    val subscriptionsPath: String,
    val usersPath: String,
    val timeout: Duration,
)
