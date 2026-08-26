package me.rgunny.kachi.user.adapter.inbound.web.dto

import me.rgunny.kachi.user.application.port.inbound.subscription.model.SubscriptionResult
import me.rgunny.kachi.user.domain.SubscriptionChannel
import java.time.Instant

/**
 * 구독 응답.
 * [id]는 구독 id이고 [name]은 키워드의 최초 등록 원문이다.
 */
data class SubscriptionResponse(
    val id: String,
    val keywordId: String,
    val name: String,
    val canonicalKey: String,
    val channels: Set<SubscriptionChannel>,
    val enabled: Boolean,
    val registeredAt: Instant,
    val disabledAt: Instant?
) {

    companion object {

        fun from(result: SubscriptionResult): SubscriptionResponse {
            return SubscriptionResponse(
                id = result.id.value.toString(),
                keywordId = result.keywordId.value.toString(),
                name = result.name,
                canonicalKey = result.canonicalKey,
                channels = result.channels,
                enabled = result.enabled,
                registeredAt = result.registeredAt,
                disabledAt = result.disabledAt
            )
        }
    }
}
