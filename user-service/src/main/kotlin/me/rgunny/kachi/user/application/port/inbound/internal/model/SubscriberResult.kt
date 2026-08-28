package me.rgunny.kachi.user.application.port.inbound.internal.model

import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * 수신자 한 명의 한 채널.
 * 주소는 담지 않는다. `(userId, channel)`이 바인딩 하나를 확정한다.
 */
data class SubscriberResult(
    val userId: UserId,
    val channel: SubscriptionChannel
)
