package me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram.dto

import com.fasterxml.jackson.annotation.JsonProperty

internal data class TelegramResponseParameters(
    @param:JsonProperty("retry_after")
    val retryAfter: Long? = null,
)

