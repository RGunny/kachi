package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.binding.IssueTelegramLinkUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.model.IssueTelegramLinkCommand
import me.rgunny.kachi.user.application.port.inbound.binding.model.TelegramLinkResult
import me.rgunny.kachi.user.fixture.UserTestFixture
import java.time.Duration

/**
 * 연결 링크 발급 유스케이스 대역.
 *
 * 고정 링크를 돌려준다.
 */
class FakeIssueTelegramLinkUseCase : IssueTelegramLinkUseCase {
    var exception: RuntimeException? = null
    lateinit var command: IssueTelegramLinkCommand

    override fun issue(command: IssueTelegramLinkCommand): TelegramLinkResult {
        exception?.let { throw it }
        this.command = command

        return TelegramLinkResult(linkUrl = LINK_URL, expiresAt = EXPIRES_AT)
    }

    companion object {
        const val LINK_URL = "https://t.me/kachi_test_bot?start=token"
        val EXPIRES_AT = UserTestFixture.NOW.plus(Duration.ofMinutes(10))
    }
}
