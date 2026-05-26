package me.rgunny.kachi.user.adapter.`in`.web.fake

import me.rgunny.kachi.user.application.port.`in`.DeactivateUserCommand
import me.rgunny.kachi.user.application.port.`in`.DeactivateUserUseCase

class FakeDeactivateUserUseCase : DeactivateUserUseCase {
    lateinit var command: DeactivateUserCommand
    var exception: RuntimeException? = null

    override fun deactivate(command: DeactivateUserCommand) {
        exception?.let { throw it }
        this.command = command
    }
}
