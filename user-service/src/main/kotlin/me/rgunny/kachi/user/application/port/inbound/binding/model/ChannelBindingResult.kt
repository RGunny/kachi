package me.rgunny.kachi.user.application.port.inbound.binding.model

import me.rgunny.kachi.user.domain.ChannelBinding
import me.rgunny.kachi.user.domain.ChannelBindingId
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.SubscriptionChannel
import java.time.Instant

/**
 * 사용자에게 보여주는 바인딩.
 * 주소는 마스킹한 값만 담는다.
 */
data class ChannelBindingResult(
    val id: ChannelBindingId,
    val channel: SubscriptionChannel,
    val status: ChannelBindingStatus,
    val addressMasked: String?,
    val boundAt: Instant?,
    val revokedAt: Instant?
) {

    companion object {

        fun of(binding: ChannelBinding): ChannelBindingResult {
            return ChannelBindingResult(
                id = binding.id,
                channel = binding.channel,
                status = binding.status,
                addressMasked = binding.address?.masked(),
                boundAt = binding.boundAt,
                revokedAt = binding.revokedAt
            )
        }
    }
}
