package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * 사용자에게 그 채널의 바인딩이 없다.
 */
class ChannelBindingNotFoundException(
    val userId: UserId,
    val channel: SubscriptionChannel
) : RuntimeException("채널 바인딩을 찾을 수 없습니다: channel=$channel")
