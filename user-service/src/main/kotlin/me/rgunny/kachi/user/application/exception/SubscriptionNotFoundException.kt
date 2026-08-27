package me.rgunny.kachi.user.application.exception

import me.rgunny.kachi.user.domain.SubscriptionId

/**
 * 요청한 구독이 없다.
 *
 * 존재하지 않는 id뿐 아니라 다른 사용자의 구독도 소유 검사 전에는 이 예외가 아니라 접근 거부로 구분된다.
 */
class SubscriptionNotFoundException(
    val subscriptionId: SubscriptionId
) : RuntimeException("구독을 찾을 수 없습니다: ${subscriptionId.value}")
