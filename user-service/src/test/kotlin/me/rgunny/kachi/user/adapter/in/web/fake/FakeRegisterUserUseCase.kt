package me.rgunny.kachi.user.adapter.`in`.web.fake

import me.rgunny.kachi.user.application.port.`in`.RegisterUserCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterUserResult
import me.rgunny.kachi.user.application.port.`in`.RegisterUserUseCase
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import java.time.Instant
import me.rgunny.kachi.user.fixture.UserTestFixture

class FakeRegisterUserUseCase : RegisterUserUseCase {
    var exception: RuntimeException? = null

    override fun register(command: RegisterUserCommand): RegisterUserResult {
        exception?.let { throw it }

        return RegisterUserResult(
            id = UserId.newId(),
            email = command.email,
            nickname = command.nickname,
            status = UserStatus.ACTIVE,
            role = UserRole.USER,
            authProvider = command.authProvider,
            registeredAt = REGISTERED_AT
        )
    }

    companion object {
        private val REGISTERED_AT: Instant = UserTestFixture.NOW
    }
}
