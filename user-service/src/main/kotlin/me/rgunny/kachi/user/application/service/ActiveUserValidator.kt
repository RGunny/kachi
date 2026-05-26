package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.exception.UserNotFoundException
import me.rgunny.kachi.user.application.port.out.UserPersistencePort
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserStatus
import org.springframework.stereotype.Service

/**
 * 사용자 존재 여부와 ACTIVE 상태를 검증하고, 유효한 사용자 도메인 객체를 반환한다.
 * */
@Service
class ActiveUserValidator(
    private val userPersistencePort: UserPersistencePort
) {

    fun get(userId: UserId): User {
        val user = userPersistencePort.findById(userId)
            ?: throw UserNotFoundException(userId)

        if (user.status != UserStatus.ACTIVE) {
            throw InactiveUserException(user.id, user.status)
        }

        return user
    }
}
