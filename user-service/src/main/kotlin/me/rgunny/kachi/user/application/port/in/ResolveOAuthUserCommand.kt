package me.rgunny.kachi.user.application.port.`in`

import me.rgunny.kachi.user.domain.AuthProvider

data class ResolveOAuthUserCommand(
    val authProvider: AuthProvider,
    val providerUserId: String,
    val email: String,
    val nickname: String
)
