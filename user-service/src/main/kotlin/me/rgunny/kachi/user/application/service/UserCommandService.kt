package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.port.`in`.RegisterUserCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterUserResult
import me.rgunny.kachi.user.application.port.`in`.RegisterUserUseCase
import me.rgunny.kachi.user.application.port.out.UserPersistencePort
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.User
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class UserCommandService(
    private val userPersistencePort: UserPersistencePort,
    private val clock: Clock
) : RegisterUserUseCase {

    override fun register(command: RegisterUserCommand): RegisterUserResult {
        val email = Email.of(command.email)
        val nickname = Nickname.of(command.nickname)

        if (userPersistencePort.existsByEmail(email)) {
            throw DuplicateEmailException(email)
        }

        val user = User.register(
            email = email,
            nickname = nickname,
            authProvider = command.authProvider,
            registeredAt = Instant.now(clock)
        )

        return RegisterUserResult.from(userPersistencePort.save(user))
    }
}
