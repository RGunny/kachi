package me.rgunny.kachi.user.adapter.inbound.web.dto

import me.rgunny.kachi.user.application.port.inbound.internal.model.SubscriberResult
import me.rgunny.kachi.user.domain.SubscriptionChannel

/**
 * 수신자 internal API 응답 한 건.
 * 주소는 싣지 않고 [recipientRef]로 바인딩 조회 API에 되묻게 한다.
 */
data class SubscriberResponse(
    val userId: String,
    val channel: SubscriptionChannel,
    val recipientRef: String
) {

    companion object {

        fun from(result: SubscriberResult): SubscriberResponse {
            return SubscriberResponse(
                userId = result.userId.value.toString(),
                channel = result.channel,
                recipientRef = result.recipientRef.value.toString()
            )
        }
    }
}
