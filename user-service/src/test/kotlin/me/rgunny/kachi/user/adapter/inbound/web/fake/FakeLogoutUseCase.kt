package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.auth.model.LogoutCommand
import me.rgunny.kachi.user.application.port.inbound.auth.LogoutUseCase

class FakeLogoutUseCase : LogoutUseCase {
    lateinit var command: LogoutCommand
    var exception: RuntimeException? = null

    override fun logout(command: LogoutCommand) {
        exception?.let { throw it }
        this.command = command
    }
}
