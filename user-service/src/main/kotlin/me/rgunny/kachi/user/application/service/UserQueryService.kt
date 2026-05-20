package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.exception.UserNotFoundException
import me.rgunny.kachi.user.application.port.`in`.GetUserQuery
import me.rgunny.kachi.user.application.port.`in`.GetUserResult
import me.rgunny.kachi.user.application.port.`in`.GetUserUseCase
import me.rgunny.kachi.user.application.port.out.UserPersistencePort
import me.rgunny.kachi.user.domain.UserStatus
import org.springframework.stereotype.Service

@Service
class UserQueryService(
    private val userPersistencePort: UserPersistencePort
) : GetUserUseCase {

    override fun get(query: GetUserQuery): GetUserResult {
        val user = userPersistencePort.findById(query.userId)
            ?: throw UserNotFoundException(query.userId)

        if (user.status != UserStatus.ACTIVE) {
            throw InactiveUserException(user.id, user.status)
        }

        return GetUserResult.from(user)
    }
}
