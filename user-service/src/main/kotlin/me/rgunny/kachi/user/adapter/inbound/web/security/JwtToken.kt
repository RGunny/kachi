package me.rgunny.kachi.user.adapter.inbound.web.security

import java.time.Instant

data class JwtToken(
    val id: String,
    val value: String,
    val expiresAt: Instant
)
