package me.rgunny.kachi.user.application.port.inbound.binding.model

import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * 해지할 바인딩은 사용자·채널로 특정한다. 사용자·채널당 하나라 id가 필요 없다.
 */
data class RevokeChannelBindingCommand(
    val userId: UserId,
    val channel: SubscriptionChannel
)
