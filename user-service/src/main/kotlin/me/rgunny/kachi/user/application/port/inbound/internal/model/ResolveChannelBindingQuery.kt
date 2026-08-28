package me.rgunny.kachi.user.application.port.inbound.internal.model

import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * 주소로 풀 수신자와 채널.
 */
data class ResolveChannelBindingQuery(
    val userId: UserId,
    val channel: SubscriptionChannel
)
