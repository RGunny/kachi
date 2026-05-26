package me.rgunny.kachi.user.application.port.`in`

import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import java.time.Instant

data class GetUserResult(
    val id: UserId,
    val email: String,
    val nickname: String,
    val status: UserStatus,
    val role: UserRole,
    val authProvider: AuthProvider,
    val registeredAt: Instant
) {

    companion object {

        fun from(user: User): GetUserResult {
            return GetUserResult(
                id = user.id,
                email = user.email.value,
                nickname = user.nickname.value,
                status = user.status,
                role = user.role,
                authProvider = user.authProvider,
                registeredAt = user.registeredAt
            )
        }
    }
}
