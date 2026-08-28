package me.rgunny.kachi.user.application.port.inbound.internal.model

import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * 수신자 한 명과 그 사용자가 받을 수 있는 채널.
 * [channels]는 ACTIVE 바인딩이 있는 채널이며 비어 있지 않다.
 */
data class UserChannelsResult(
    val userId: UserId,
    val channels: Set<SubscriptionChannel>
)
