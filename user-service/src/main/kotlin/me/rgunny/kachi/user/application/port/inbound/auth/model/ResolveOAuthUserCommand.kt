package me.rgunny.kachi.user.application.port.inbound.auth.model

import me.rgunny.kachi.user.domain.AuthProvider

data class ResolveOAuthUserCommand(
    val authProvider: AuthProvider,
    val providerUserId: String,
    val email: String,
    val nickname: String
)
