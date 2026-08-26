package me.rgunny.kachi.user.application.port.inbound.internal.model

import me.rgunny.kachi.user.domain.ChannelBindingId
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * 수신자 한 명의 한 채널.
 * 주소 대신 바인딩 id([recipientRef])만 담는다.
 */
data class SubscriberResult(
    val userId: UserId,
    val channel: SubscriptionChannel,
    val recipientRef: ChannelBindingId
)
