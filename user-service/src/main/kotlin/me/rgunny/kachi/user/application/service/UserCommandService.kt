package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.port.`in`.DeactivateUserCommand
import me.rgunny.kachi.user.application.port.`in`.DeactivateUserUseCase
import me.rgunny.kachi.user.application.port.`in`.RegisterUserCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterUserResult
import me.rgunny.kachi.user.application.port.`in`.RegisterUserUseCase
import me.rgunny.kachi.user.application.port.out.UserPersistencePort
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.ProviderUserId
import me.rgunny.kachi.user.domain.User
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class UserCommandService(
    private val userPersistencePort: UserPersistencePort,
    private val clock: Clock,
    private val activeUserValidator: ActiveUserValidator
) : RegisterUserUseCase, DeactivateUserUseCase {

    override fun register(command: RegisterUserCommand): RegisterUserResult {
        val email = Email.of(command.email)
        val nickname = Nickname.of(command.nickname)
        val providerUserId = command.providerUserId?.let(ProviderUserId::of)

        if (userPersistencePort.existsByEmail(email)) {
            throw DuplicateEmailException(email)
        }

        val user = User.register(
            email = email,
            nickname = nickname,
            authProvider = command.authProvider,
            providerUserId = providerUserId,
            registeredAt = Instant.now(clock)
        )

        return RegisterUserResult.from(userPersistencePort.save(user))
    }

    override fun deactivate(command: DeactivateUserCommand) {
        // 1. 탈퇴 대상 사용자가 현재 활성 상태인지 확인한다.
        val user = activeUserValidator.get(command.userId)

        // 2. 사용자 상태를 DELETED로 변경하고 탈퇴 시각을 기록한다.
        userPersistencePort.save(user.deactivate(Instant.now(clock)))
    }
}
