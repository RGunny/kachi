package me.rgunny.kachi.user.application.port.inbound.binding

import me.rgunny.kachi.user.application.port.inbound.binding.model.IssueTelegramLinkCommand
import me.rgunny.kachi.user.application.port.inbound.binding.model.TelegramLinkResult

/**
 * 연결 링크 발급.
 *
 * 바인딩을 PENDING으로 두고 사용자가 봇에 보낼 `/start` 링크를 돌려준다. 다시 발급하면 이전 토큰은 무효가 된다.
 */
interface IssueTelegramLinkUseCase {

    fun issue(command: IssueTelegramLinkCommand): TelegramLinkResult
}
