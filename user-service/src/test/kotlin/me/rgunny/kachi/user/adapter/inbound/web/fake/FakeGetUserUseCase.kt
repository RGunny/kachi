package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.user.model.GetUserQuery
import me.rgunny.kachi.user.application.port.inbound.user.model.GetUserResult
import me.rgunny.kachi.user.application.port.inbound.user.GetUserUseCase
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import java.time.Instant
import me.rgunny.kachi.user.fixture.UserTestFixture

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
        private val REGISTERED_AT: Instant = UserTestFixture.NOW
    }
}
