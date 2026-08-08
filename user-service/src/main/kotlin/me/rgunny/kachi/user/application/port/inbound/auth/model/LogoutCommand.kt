package me.rgunny.kachi.user.application.port.inbound.auth.model

data class LogoutCommand(
    val refreshToken: String
)
