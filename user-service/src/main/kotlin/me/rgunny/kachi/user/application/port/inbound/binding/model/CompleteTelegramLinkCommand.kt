package me.rgunny.kachi.user.application.port.inbound.binding.model

/**
 * [token]은 봇이 `/start` 뒤에서 받은 원문, [chatId]는 그 메시지를 보낸 chat의 id다.
 */
data class CompleteTelegramLinkCommand(
    val token: String,
    val chatId: String
)
