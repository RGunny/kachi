package me.rgunny.kachi.user.adapter.outbound.persistence.subscription

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.Subscription
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.domain.UserId

/**
 * `subscriptions` 테이블과 채널 컬렉션 테이블 `subscription_channels`.
 */
@Entity
@Table(
    name = "subscriptions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_subscriptions_user_id_keyword_id", columnNames = ["user_id", "keyword_id"])
    ]
)
class SubscriptionJpaEntity(

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    val id: UUID,

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,

    @Column(name = "keyword_id", nullable = false, columnDefinition = "BINARY(16)")
    val keywordId: UUID,

    // 채널은 독립 조회가 없는 값 집합이라 별도 엔티티 없이 컬렉션 테이블로 둔다.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "subscription_channels",
        joinColumns = [JoinColumn(name = "subscription_id")]
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    val channels: MutableSet<SubscriptionChannel>,

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean,

    @Column(name = "registered_at", nullable = false)
    val registeredAt: Instant,

    @Column(name = "disabled_at")
    val disabledAt: Instant?
) {

    companion object {
        fun from(subscription: Subscription): SubscriptionJpaEntity {
            return SubscriptionJpaEntity(
                id = subscription.id.value,
                userId = subscription.userId.value,
                keywordId = subscription.keywordId.value,
                channels = subscription.channels.toMutableSet(),
                enabled = subscription.enabled,
                registeredAt = subscription.registeredAt,
                disabledAt = subscription.disabledAt
            )
        }
    }

    fun toDomain(): Subscription {
        return Subscription.restore(
            id = SubscriptionId.of(id),
            userId = UserId.of(userId),
            keywordId = KeywordId.of(keywordId),
            channels = channels.toSet(),
            enabled = enabled,
            registeredAt = registeredAt,
            disabledAt = disabledAt
        )
    }
}
