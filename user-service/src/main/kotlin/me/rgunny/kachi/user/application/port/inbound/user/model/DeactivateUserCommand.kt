package me.rgunny.kachi.user.application.port.inbound.user.model

import me.rgunny.kachi.user.domain.UserId

data class DeactivateUserCommand(
    val userId: UserId
)
