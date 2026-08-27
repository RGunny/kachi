package me.rgunny.kachi.user.application.port.inbound.internal.model

import me.rgunny.kachi.user.domain.ChannelBindingId

/**
 * 주소로 풀 수신처 참조.
 */
data class ResolveChannelBindingQuery(
    val ref: ChannelBindingId
)
