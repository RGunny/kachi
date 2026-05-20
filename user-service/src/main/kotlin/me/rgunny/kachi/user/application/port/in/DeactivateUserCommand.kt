package me.rgunny.kachi.user.application.port.`in`

import me.rgunny.kachi.user.domain.UserId

data class DeactivateUserCommand(
    val userId: UserId
)
