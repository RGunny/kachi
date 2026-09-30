package me.rgunny.kachi.user.adapter.outbound.persistence.subscription

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

/**
 * `subscriptions` 테이블 Spring Data 저장소.
 *
 * 사용자 기준 목록, 키워드 기준 enabled 목록, (user, keyword) 존재 여부를 제공한다. 채널 컬렉션은 엔티티가 함께 적재한다.
 */
interface SubscriptionJpaRepository : JpaRepository<SubscriptionJpaEntity, UUID> {
    fun findAllByUserId(userId: UUID): List<SubscriptionJpaEntity>

    fun findAllByKeywordIdAndEnabledTrue(keywordId: UUID): List<SubscriptionJpaEntity>

    fun findByUserIdAndKeywordId(userId: UUID, keywordId: UUID): SubscriptionJpaEntity?

    fun existsByUserIdAndKeywordId(userId: UUID, keywordId: UUID): Boolean
}
