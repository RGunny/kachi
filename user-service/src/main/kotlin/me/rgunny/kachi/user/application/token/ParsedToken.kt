package me.rgunny.kachi.user.application.token

import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole

data class ParsedToken(
    val userId: UserId,
    val type: TokenType,
    val role: UserRole?
)
