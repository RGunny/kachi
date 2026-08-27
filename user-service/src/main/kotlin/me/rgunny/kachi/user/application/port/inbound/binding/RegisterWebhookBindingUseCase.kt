package me.rgunny.kachi.user.application.port.inbound.binding

import me.rgunny.kachi.user.application.port.inbound.binding.model.ChannelBindingResult
import me.rgunny.kachi.user.application.port.inbound.binding.model.RegisterWebhookBindingCommand

/**
 * 주소를 직접 받는 채널의 바인딩 등록.
 *
 * 이미 있으면 주소를 교체하고, 해지돼 있으면 되살린다.
 */
interface RegisterWebhookBindingUseCase {

    fun register(command: RegisterWebhookBindingCommand): ChannelBindingResult
}
