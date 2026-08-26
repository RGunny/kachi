package me.rgunny.kachi.user.application.port.inbound.subscription.model

import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * [name]은 사용자가 입력한 원문이다.
 * 정규화는 서비스가 한다.
 * */
data class RegisterSubscriptionCommand(
    val userId: UserId,
    val name: String,
    val channels: Set<SubscriptionChannel>
)
