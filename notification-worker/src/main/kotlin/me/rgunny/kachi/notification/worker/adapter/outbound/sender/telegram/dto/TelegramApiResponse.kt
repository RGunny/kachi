package me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram.dto

import com.fasterxml.jackson.annotation.JsonProperty

internal data class TelegramApiResponse(
    val ok: Boolean = false,
    @param:JsonProperty("error_code")
    val errorCode: Int? = null,
    val description: String? = null,
    val parameters: TelegramResponseParameters? = null,
)

