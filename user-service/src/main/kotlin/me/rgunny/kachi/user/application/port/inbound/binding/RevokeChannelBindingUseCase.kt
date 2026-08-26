package me.rgunny.kachi.user.application.port.inbound.binding

import me.rgunny.kachi.user.application.port.inbound.binding.model.RevokeChannelBindingCommand

/**
 * 채널 바인딩 해지.
 * 주소를 지우고 REVOKED로 둔다.
 * 구독은 건드리지 않는다.
 */
interface RevokeChannelBindingUseCase {

    fun revoke(command: RevokeChannelBindingCommand)
}
