package me.rgunny.kachi.user.application.port.outbound.subscription

import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.Subscription
import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.domain.UserId

/**
 * 구독 저장소 출력 포트.
 *
 * (user, keyword) 중복 판정과 사용자별 목록 조회를 제공한다. 키워드 기준 조회는 라우팅 API가 생길 때 추가한다.
 */
interface SubscriptionPersistencePort {
    fun findById(subscriptionId: SubscriptionId): Subscription?

    fun findAllByUserId(userId: UserId): List<Subscription>

    fun existsByUserIdAndKeywordId(userId: UserId, keywordId: KeywordId): Boolean

    fun save(subscription: Subscription): Subscription
}
