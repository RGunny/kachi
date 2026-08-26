package me.rgunny.kachi.user.adapter.inbound.web.dto

import me.rgunny.kachi.user.application.port.inbound.binding.model.ChannelBindingResult
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.SubscriptionChannel
import java.time.Instant

/**
 * 바인딩 응답. 주소는 마스킹한 값만 나간다.
 */
data class ChannelBindingResponse(
    val channel: SubscriptionChannel,
    val status: ChannelBindingStatus,
    val addressMasked: String?,
    val boundAt: Instant?,
    val revokedAt: Instant?
) {

    companion object {

        fun from(result: ChannelBindingResult): ChannelBindingResponse {
            return ChannelBindingResponse(
                channel = result.channel,
                status = result.status,
                addressMasked = result.addressMasked,
                boundAt = result.boundAt,
                revokedAt = result.revokedAt
            )
        }
    }
}
