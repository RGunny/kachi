package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.auth.model.RefreshTokenCommand
import me.rgunny.kachi.user.application.port.inbound.auth.model.RefreshTokenResult
import me.rgunny.kachi.user.application.port.inbound.auth.RefreshTokenUseCase
import java.time.Duration
import java.time.Instant
import me.rgunny.kachi.user.fixture.UserTestFixture

class FakeRefreshTokenUseCase : RefreshTokenUseCase {
    lateinit var command: RefreshTokenCommand
    var exception: RuntimeException? = null

    override fun refresh(command: RefreshTokenCommand): RefreshTokenResult {
        exception?.let { throw it }
        this.command = command

        return RefreshTokenResult(
            accessToken = "access-token",
            accessTokenExpiresAt = ACCESS_TOKEN_EXPIRES_AT,
            refreshToken = "refresh-token",
            refreshTokenExpiresAt = REFRESH_TOKEN_EXPIRES_AT
        )
    }

    companion object {
        private val ACCESS_TOKEN_EXPIRES_AT: Instant = UserTestFixture.NOW.plus(Duration.ofMinutes(15))
        private val REFRESH_TOKEN_EXPIRES_AT: Instant = UserTestFixture.NOW.plus(Duration.ofDays(14))
    }
}
