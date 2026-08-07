package me.rgunny.kachi.user.application.port.inbound.user.model

import me.rgunny.kachi.user.domain.AuthProvider

data class RegisterUserCommand(
    val email: String,
    val nickname: String,
    val authProvider: AuthProvider,
    val providerUserId: String? = null
)
