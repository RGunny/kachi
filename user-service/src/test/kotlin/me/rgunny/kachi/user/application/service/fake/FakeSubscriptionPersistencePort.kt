package me.rgunny.kachi.user.application.service.fake

import me.rgunny.kachi.user.application.port.outbound.subscription.SubscriptionPersistencePort
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.Subscription
import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.domain.UserId

/**
 * 메모리 구독 저장소.
 *
 * 초기 구독을 넣어 두고 서비스가 저장한 결과를 [savedSubscriptions]로 확인한다.
 * [existsCalled]는 사용자 검증 실패 시 저장소에 손대지 않았음을 단언하기 위한 것이다.
 */
class FakeSubscriptionPersistencePort(
    subscriptions: List<Subscription> = emptyList()
) : SubscriptionPersistencePort {
    private val subscriptions = subscriptions.associateBy { it.id }.toMutableMap()
    val savedSubscriptions = mutableListOf<Subscription>()
    var existsCalled = false

    override fun findById(subscriptionId: SubscriptionId): Subscription? = subscriptions[subscriptionId]

    override fun findAllByUserId(userId: UserId): List<Subscription> = subscriptions.values.filter { it.userId == userId }

    override fun existsByUserIdAndKeywordId(userId: UserId, keywordId: KeywordId): Boolean {
        existsCalled = true
        return subscriptions.values.any { it.userId == userId && it.keywordId == keywordId }
    }

    override fun save(subscription: Subscription): Subscription {
        savedSubscriptions += subscription
        subscriptions[subscription.id] = subscription
        return subscription
    }
}
