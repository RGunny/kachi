package me.rgunny.kachi.user.application.port.outbound.subscription

import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.Subscription
import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.domain.UserId

/**
 * 구독 저장소 출력 포트.
 *
 * (user, keyword) 중복 판정, 사용자별 목록, 키워드별 enabled 구독 조회를 제공한다.
 */
interface SubscriptionPersistencePort {
    fun findById(subscriptionId: SubscriptionId): Subscription?

    fun findAllByUserId(userId: UserId): List<Subscription>

    /** 키워드의 enabled 구독 */
    fun findAllEnabledByKeywordId(keywordId: KeywordId): List<Subscription>

    fun existsByUserIdAndKeywordId(userId: UserId, keywordId: KeywordId): Boolean

    fun save(subscription: Subscription): Subscription
}
