package me.rgunny.kachi.user.adapter.inbound.web.dto

import me.rgunny.kachi.user.application.port.inbound.internal.model.SubscriberResult
import me.rgunny.kachi.user.domain.SubscriptionChannel

/**
 * 수신자 internal API 응답 한 건.
 * 주소는 싣지 않는다. 발송 쪽은 `(userId, channel)`로 바인딩 조회 API에 되묻는다.
 */
data class SubscriberResponse(
    val userId: String,
    val channel: SubscriptionChannel
) {

    companion object {

        fun from(result: SubscriberResult): SubscriberResponse {
            return SubscriberResponse(
                userId = result.userId.value.toString(),
                channel = result.channel
            )
        }
    }
}
