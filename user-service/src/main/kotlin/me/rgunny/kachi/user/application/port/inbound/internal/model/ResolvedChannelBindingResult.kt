package me.rgunny.kachi.user.application.port.inbound.internal.model

import me.rgunny.kachi.user.domain.ChannelBinding
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.SubscriptionChannel

/**
 * 발송에 쓰는 바인딩.
 * [address]는 평문이며 ACTIVE일 때만 있다.
 */
data class ResolvedChannelBindingResult(
    val channel: SubscriptionChannel,
    val status: ChannelBindingStatus,
    val address: String?
) {

    companion object {

        fun of(binding: ChannelBinding): ResolvedChannelBindingResult {
            return ResolvedChannelBindingResult(
                channel = binding.channel,
                status = binding.status,
                address = binding.address?.value
            )
        }
    }
}
