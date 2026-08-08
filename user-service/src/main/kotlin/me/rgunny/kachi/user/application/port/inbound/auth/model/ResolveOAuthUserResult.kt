package me.rgunny.kachi.user.application.port.inbound.auth.model

import me.rgunny.kachi.user.domain.UserId

data class ResolveOAuthUserResult(
    val userId: UserId
)
