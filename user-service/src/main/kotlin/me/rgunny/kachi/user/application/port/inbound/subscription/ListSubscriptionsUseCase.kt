package me.rgunny.kachi.user.application.port.inbound.subscription

import me.rgunny.kachi.user.application.port.inbound.subscription.model.ListSubscriptionsQuery
import me.rgunny.kachi.user.application.port.inbound.subscription.model.SubscriptionResult

/**
 * 내 구독 목록 조회.
 *
 * 구독마다 키워드의 원문 이름과 정규화 값을 함께 돌려준다.
 */
interface ListSubscriptionsUseCase {

    fun list(query: ListSubscriptionsQuery): List<SubscriptionResult>
}
