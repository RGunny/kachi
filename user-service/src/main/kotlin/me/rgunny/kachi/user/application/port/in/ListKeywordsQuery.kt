package me.rgunny.kachi.user.application.port.`in`

import me.rgunny.kachi.user.domain.UserId

data class ListKeywordsQuery(
    val userId: UserId
)
