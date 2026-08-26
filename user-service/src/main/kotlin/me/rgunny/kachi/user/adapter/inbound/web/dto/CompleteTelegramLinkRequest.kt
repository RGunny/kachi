package me.rgunny.kachi.user.adapter.inbound.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 봇이 받은 `/start <token>`과 그 메시지를 보낸 chat id.
 */
data class CompleteTelegramLinkRequest(

    @field:NotBlank
    @field:Size(max = 100)
    val token: String,

    @field:NotBlank
    @field:Size(max = 32)
    val chatId: String
)
