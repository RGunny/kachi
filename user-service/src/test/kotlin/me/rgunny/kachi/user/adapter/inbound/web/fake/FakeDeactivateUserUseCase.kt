package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.user.model.DeactivateUserCommand
import me.rgunny.kachi.user.application.port.inbound.user.DeactivateUserUseCase

class FakeDeactivateUserUseCase : DeactivateUserUseCase {
    lateinit var command: DeactivateUserCommand
    var exception: RuntimeException? = null

    override fun deactivate(command: DeactivateUserCommand) {
        exception?.let { throw it }
        this.command = command
    }
}
