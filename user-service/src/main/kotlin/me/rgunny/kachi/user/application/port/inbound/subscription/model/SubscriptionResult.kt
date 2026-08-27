package me.rgunny.kachi.user.application.port.inbound.subscription.model

import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.Subscription
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.domain.UserId
import java.time.Instant

/**
 * 구독 유스케이스의 공통 결과.
 *
 * 구독 자체와 그 구독이 가리키는 키워드의 이름·정규화 값을 함께 담는다. 등록·수정·목록이 같은 모양을 돌려준다.
 */
data class SubscriptionResult(
    val id: SubscriptionId,
    val userId: UserId,
    val keywordId: KeywordId,
    val name: String,
    val canonicalKey: String,
    val channels: Set<SubscriptionChannel>,
    val enabled: Boolean,
    val registeredAt: Instant,
    val disabledAt: Instant?
) {

    companion object {

        fun of(subscription: Subscription, keyword: Keyword): SubscriptionResult {
            return SubscriptionResult(
                id = subscription.id,
                userId = subscription.userId,
                keywordId = keyword.id,
                name = keyword.displayName.value,
                canonicalKey = keyword.canonicalKey.value,
                channels = subscription.channels,
                enabled = subscription.enabled,
                registeredAt = subscription.registeredAt,
                disabledAt = subscription.disabledAt
            )
        }
    }
}
