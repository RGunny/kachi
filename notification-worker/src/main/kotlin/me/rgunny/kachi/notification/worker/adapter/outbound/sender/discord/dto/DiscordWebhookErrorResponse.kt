package me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

internal data class DiscordWebhookErrorResponse(
    @param:JsonProperty("retry_after")
    val retryAfter: BigDecimal? = null,
)

