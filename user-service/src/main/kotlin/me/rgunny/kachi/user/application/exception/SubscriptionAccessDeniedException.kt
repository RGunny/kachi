package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.domain.UserId

/**
 * 구독은 있으나 요청 사용자의 것이 아니다.
 *
 * 404가 아니라 403으로 응답해 존재 여부를 숨기지 않는다. 구독 id는 추측 가능한 값이 아니다.
 */
class SubscriptionAccessDeniedException(
    val subscriptionId: SubscriptionId,
    val userId: UserId
) : RuntimeException("구독에 접근할 수 없습니다: subscriptionId=${subscriptionId.value}, userId=${userId.value}")
