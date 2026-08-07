package me.rgunny.kachi.user.application.port.inbound.auth.model

data class RefreshTokenCommand(
    val refreshToken: String
)
