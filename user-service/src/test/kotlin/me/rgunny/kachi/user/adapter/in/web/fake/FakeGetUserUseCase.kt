package me.rgunny.kachi.user.adapter.`in`.web.fake

import me.rgunny.kachi.user.application.port.`in`.GetUserQuery
import me.rgunny.kachi.user.application.port.`in`.GetUserResult
import me.rgunny.kachi.user.application.port.`in`.GetUserUseCase
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import java.time.Instant

class FakeGetUserUseCase : GetUserUseCase {
    var exception: RuntimeException? = null

    override fun get(query: GetUserQuery): GetUserResult {
        exception?.let { throw it }

        return GetUserResult(
            id = query.userId,
            email = "rgunny@kachi.com",
            nickname = "rgunny",
            status = UserStatus.ACTIVE,
            role = UserRole.USER,
            authProvider = AuthProvider.GOOGLE,
            registeredAt = REGISTERED_AT
        )
    }

    companion object {
        private val REGISTERED_AT: Instant = Instant.parse("2026-05-20T00:00:00Z")
    }
}
