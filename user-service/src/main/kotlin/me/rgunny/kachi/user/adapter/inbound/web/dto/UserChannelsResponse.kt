package me.rgunny.kachi.user.adapter.inbound.web.dto

import me.rgunny.kachi.user.application.port.inbound.internal.model.UserChannelsResult
import me.rgunny.kachi.user.domain.SubscriptionChannel

/**
 * 역할별 수신자 internal API 응답 한 건.
 * [channels]는 ACTIVE 바인딩이 있는 채널이며 주소는 싣지 않는다.
 */
data class UserChannelsResponse(
    val userId: String,
    val channels: List<SubscriptionChannel>
) {

    companion object {

        fun from(result: UserChannelsResult): UserChannelsResponse {
            return UserChannelsResponse(
                userId = result.userId.value.toString(),
                channels = result.channels.sorted()
            )
        }
    }
}
