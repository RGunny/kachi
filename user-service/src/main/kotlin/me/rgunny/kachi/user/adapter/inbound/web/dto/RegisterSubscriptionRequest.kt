package me.rgunny.kachi.user.adapter.inbound.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import me.rgunny.kachi.user.domain.SubscriptionChannel

/**
 * 구독 등록 요청.
 * 채널은 하나 이상이어야 한다.
 */
data class RegisterSubscriptionRequest(

    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    @field:NotEmpty
    val channels: Set<SubscriptionChannel>
)
