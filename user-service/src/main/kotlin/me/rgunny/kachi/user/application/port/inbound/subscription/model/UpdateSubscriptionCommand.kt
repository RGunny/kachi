package me.rgunny.kachi.user.application.port.inbound.subscription.model

import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.domain.UserId

/**
 * null인 필드는 변경하지 않는다. 둘 다 null이면 의미 없는 요청이라 거부한다.
 */
data class UpdateSubscriptionCommand(
    val subscriptionId: SubscriptionId,
    val userId: UserId,
    val channels: Set<SubscriptionChannel>? = null,
    val enabled: Boolean? = null
) {

    init {
        require(channels != null || enabled != null) { "수정할 구독 값이 필요합니다" }
    }
}
