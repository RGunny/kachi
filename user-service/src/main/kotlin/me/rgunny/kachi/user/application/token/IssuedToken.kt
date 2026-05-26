package me.rgunny.kachi.user.application.token

import java.time.Instant

data class IssuedToken(
    val id: String,
    val value: String,
    val expiresAt: Instant
)
