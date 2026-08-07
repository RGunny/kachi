package me.rgunny.kachi.user.application.port.inbound.keyword.model

import me.rgunny.kachi.user.domain.UserId

data class RegisterKeywordCommand(
    val userId: UserId,
    val name: String
)
