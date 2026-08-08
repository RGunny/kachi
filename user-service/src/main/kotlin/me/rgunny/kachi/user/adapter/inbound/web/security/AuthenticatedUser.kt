package me.rgunny.kachi.user.adapter.inbound.web.security

import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole

data class AuthenticatedUser(
    val userId: UserId,
    val role: UserRole
)
