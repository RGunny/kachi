package me.rgunny.kachi.user.domain

import java.time.Instant

/**
 * 사용자의 키워드 구독.
 *
 * 사용자·키워드당 하나이며 채널을 하나 이상 가진다.
 * 비활성화하면 수집·알림 대상에서 빠지고 다시 활성화할 수 있다.
 */
class Subscription private constructor(
    val id: SubscriptionId,
    val userId: UserId,
    val keywordId: KeywordId,
    val channels: Set<SubscriptionChannel>,
    val enabled: Boolean,
    val registeredAt: Instant,
    val disabledAt: Instant?
) {
    init {
        require(channels.isNotEmpty()) { "구독 채널은 하나 이상이어야 합니다" }
    }

    companion object {

        fun create(
            userId: UserId,
            keywordId: KeywordId,
            channels: Set<SubscriptionChannel>,
            registeredAt: Instant
        ): Subscription {
            return Subscription(
                id = SubscriptionId.newId(),
                userId = userId,
                keywordId = keywordId,
                channels = channels.toSet(),
                enabled = true,
                registeredAt = registeredAt,
                disabledAt = null
            )
        }

        fun restore(
            id: SubscriptionId,
            userId: UserId,
            keywordId: KeywordId,
            channels: Set<SubscriptionChannel>,
            enabled: Boolean,
            registeredAt: Instant,
            disabledAt: Instant?
        ): Subscription {
            return Subscription(
                id = id,
                userId = userId,
                keywordId = keywordId,
                channels = channels.toSet(),
                enabled = enabled,
                registeredAt = registeredAt,
                disabledAt = disabledAt
            )
        }
    }

    fun changeChannels(channels: Set<SubscriptionChannel>): Subscription {
        return copy(channels = channels.toSet())
    }

    fun enable(): Subscription {
        return copy(enabled = true, disabledAt = null)
    }

    fun disable(disabledAt: Instant): Subscription {
        require(enabled) { "이미 비활성화된 구독입니다" }

        return copy(enabled = false, disabledAt = disabledAt)
    }

    private fun copy(
        channels: Set<SubscriptionChannel> = this.channels,
        enabled: Boolean = this.enabled,
        disabledAt: Instant? = this.disabledAt
    ): Subscription {
        return Subscription(
            id = id,
            userId = userId,
            keywordId = keywordId,
            channels = channels,
            enabled = enabled,
            registeredAt = registeredAt,
            disabledAt = disabledAt
        )
    }
}
