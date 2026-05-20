package me.rgunny.kachi.user.application.port.`in`

import me.rgunny.kachi.user.domain.UserId

data class GetUserQuery(
    val userId: UserId
)
