package me.rgunny.kachi.user.adapter.inbound.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 주소를 직접 받는 채널의 바인딩 등록 요청.
 * 채널은 경로에 있다.
 */
data class RegisterWebhookBindingRequest(

    @field:NotBlank
    @field:Size(max = 512)
    val webhookUrl: String
)
