package me.rgunny.kachi.user.adapter.`in`.web.fake

import me.rgunny.kachi.user.application.port.`in`.LogoutCommand
import me.rgunny.kachi.user.application.port.`in`.LogoutUseCase

class FakeLogoutUseCase : LogoutUseCase {
    lateinit var command: LogoutCommand
    var exception: RuntimeException? = null

    override fun logout(command: LogoutCommand) {
        exception?.let { throw it }
        this.command = command
    }
}
