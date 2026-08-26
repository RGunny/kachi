package me.rgunny.kachi.user.application.port.inbound.binding.model

import me.rgunny.kachi.user.domain.UserId

/**
 * 바인딩 목록을 볼 사용자.
 */
data class ListChannelBindingsQuery(
    val userId: UserId
)
