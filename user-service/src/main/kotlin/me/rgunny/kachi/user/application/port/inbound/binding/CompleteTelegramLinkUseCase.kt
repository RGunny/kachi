package me.rgunny.kachi.user.application.port.inbound.binding

import me.rgunny.kachi.user.application.port.inbound.binding.model.CompleteTelegramLinkCommand

/**
 * 봇이 받은 `/start <token>`으로 바인딩을 ACTIVE로 만든다.
 */
interface CompleteTelegramLinkUseCase {

    fun complete(command: CompleteTelegramLinkCommand)
}
