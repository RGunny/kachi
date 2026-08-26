package me.rgunny.kachi.user.adapter.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * `subscriptions` 테이블 Spring Data 저장소.
 *
 * 사용자 기준 목록과 (user, keyword) 존재 여부를 제공한다. 채널 컬렉션은 엔티티가 함께 적재한다.
 */
interface SubscriptionJpaRepository : JpaRepository<SubscriptionJpaEntity, UUID> {
    fun findAllByUserId(userId: UUID): List<SubscriptionJpaEntity>

    fun findByUserIdAndKeywordId(userId: UUID, keywordId: UUID): SubscriptionJpaEntity?

    fun existsByUserIdAndKeywordId(userId: UUID, keywordId: UUID): Boolean
}
