package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.SubscriptionChannel

/**
 * 채널 주소가 그 채널의 형식이 아니다.
 * 어떤 값이었는지는 메시지에 싣지 않는다.
 */
class InvalidChannelAddressException(
    val channel: SubscriptionChannel,
    reason: String
) : RuntimeException(reason)
