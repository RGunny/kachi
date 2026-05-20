package me.rgunny.kachi.user.application.token

import java.time.Instant

data class IssuedToken(
    val value: String,
    val expiresAt: Instant
)
