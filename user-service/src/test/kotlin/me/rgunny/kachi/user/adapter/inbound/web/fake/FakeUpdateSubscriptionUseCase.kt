package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.subscription.UpdateSubscriptionUseCase
import me.rgunny.kachi.user.application.port.inbound.subscription.model.SubscriptionResult
import me.rgunny.kachi.user.application.port.inbound.subscription.model.UpdateSubscriptionCommand
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.fixture.UserTestFixture
import java.time.Duration

/**
 * 구독 수정 유스케이스 대역.
 *
 * 커맨드의 채널·활성 값을 결과에 반영하고 비활성화면 [DISABLED_AT]을 채운다.
 * 키워드 이름은 고정값이다.
 */
class FakeUpdateSubscriptionUseCase : UpdateSubscriptionUseCase {
    var exception: RuntimeException? = null
    lateinit var command: UpdateSubscriptionCommand

    override fun update(command: UpdateSubscriptionCommand): SubscriptionResult {
        exception?.let { throw it }
        this.command = command

        return SubscriptionResult(
            id = command.subscriptionId,
            userId = command.userId,
            keywordId = KeywordId.newId(),
            name = "Trump",
            canonicalKey = "trump",
            channels = command.channels ?: setOf(SubscriptionChannel.SLACK),
            enabled = command.enabled ?: true,
            registeredAt = UserTestFixture.NOW,
            disabledAt = if (command.enabled == false) DISABLED_AT else null
        )
    }

    companion object {
        val DISABLED_AT = UserTestFixture.NOW.plus(Duration.ofHours(1))
    }
}
