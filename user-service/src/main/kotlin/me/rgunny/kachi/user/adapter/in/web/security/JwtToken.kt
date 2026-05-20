package me.rgunny.kachi.user.adapter.`in`.web.security

import java.time.Instant

data class JwtToken(
    val value: String,
    val expiresAt: Instant
)
