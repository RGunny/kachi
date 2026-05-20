package me.rgunny.kachi.user.adapter.`in`.web.dto

import me.rgunny.kachi.user.application.port.`in`.RefreshTokenResult
import java.time.Instant

data class TokenResponse(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant
) {

    companion object {

        fun from(result: RefreshTokenResult): TokenResponse {
            return TokenResponse(
                accessToken = result.accessToken,
                accessTokenExpiresAt = result.accessTokenExpiresAt,
                refreshToken = result.refreshToken,
                refreshTokenExpiresAt = result.refreshTokenExpiresAt
            )
        }
    }
}
