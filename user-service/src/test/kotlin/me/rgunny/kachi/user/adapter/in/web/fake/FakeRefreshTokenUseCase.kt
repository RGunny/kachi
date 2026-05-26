package me.rgunny.kachi.user.adapter.`in`.web.fake

import me.rgunny.kachi.user.application.port.`in`.RefreshTokenCommand
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenResult
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenUseCase
import java.time.Instant

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
        private val ACCESS_TOKEN_EXPIRES_AT: Instant = Instant.parse("2026-05-20T00:15:00Z")
        private val REFRESH_TOKEN_EXPIRES_AT: Instant = Instant.parse("2026-06-03T00:00:00Z")
    }
}
