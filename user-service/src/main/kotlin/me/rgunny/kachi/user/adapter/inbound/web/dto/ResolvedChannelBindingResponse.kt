package me.rgunny.kachi.user.adapter.inbound.web.dto

import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolvedChannelBindingResult
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.SubscriptionChannel

/**
 * 바인딩 조회 internal API 응답.
 * [address]는 평문이며 ACTIVE일 때만 있다.
 * 이 응답은 발송자 외에 노출되면 안 된다.
 */
data class ResolvedChannelBindingResponse(
    val channel: SubscriptionChannel,
    val status: ChannelBindingStatus,
    val address: String?
) {

    companion object {

        fun from(result: ResolvedChannelBindingResult): ResolvedChannelBindingResponse {
            return ResolvedChannelBindingResponse(
                channel = result.channel,
                status = result.status,
                address = result.address
            )
        }
    }
}
