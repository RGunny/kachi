package me.rgunny.kachi.user.application.port.`in`

import java.time.Instant

data class RefreshTokenResult(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant
)
