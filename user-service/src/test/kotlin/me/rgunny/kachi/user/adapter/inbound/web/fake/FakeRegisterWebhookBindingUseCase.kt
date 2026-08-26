package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.binding.RegisterWebhookBindingUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.model.ChannelBindingResult
import me.rgunny.kachi.user.application.port.inbound.binding.model.RegisterWebhookBindingCommand
import me.rgunny.kachi.user.domain.ChannelAddress
import me.rgunny.kachi.user.domain.ChannelBindingId
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.fixture.UserTestFixture

/**
 * webhook 바인딩 등록 유스케이스 대역.
 * 받은 커맨드를 [command]에 남기고 그 주소를 마스킹한 ACTIVE 결과를 돌려준다.
 */
class FakeRegisterWebhookBindingUseCase : RegisterWebhookBindingUseCase {
    var exception: RuntimeException? = null
    lateinit var command: RegisterWebhookBindingCommand

    override fun register(command: RegisterWebhookBindingCommand): ChannelBindingResult {
        exception?.let { throw it }
        this.command = command

        return ChannelBindingResult(
            id = ChannelBindingId.newId(),
            channel = command.channel,
            status = ChannelBindingStatus.ACTIVE,
            addressMasked = ChannelAddress.of(command.channel, command.webhookUrl).masked(),
            boundAt = UserTestFixture.NOW,
            revokedAt = null
        )
    }
}
