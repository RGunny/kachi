package me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram.dto

import com.fasterxml.jackson.annotation.JsonProperty

internal data class TelegramSendMessageRequest(
    @param:JsonProperty("chat_id")
    val chatId: String,
    val text: String,
)

