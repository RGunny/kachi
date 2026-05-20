package me.rgunny.kachi.user.adapter.`in`.web.security

import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole

data class JwtTokenClaims(
    val id: String,
    val userId: UserId,
    val type: JwtTokenType,
    val role: UserRole?
)
