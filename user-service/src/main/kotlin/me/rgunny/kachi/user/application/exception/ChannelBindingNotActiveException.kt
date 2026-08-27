package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * 구독 채널로 쓰려면 그 채널의 바인딩이 ACTIVE여야 하는데, 없거나 PENDING·REVOKED다.
 */
class ChannelBindingNotActiveException(
    val userId: UserId,
    val channel: SubscriptionChannel
) : RuntimeException("연결된 채널이 아닙니다: channel=$channel")
