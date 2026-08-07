package me.rgunny.kachi.user.application.port.inbound.keyword.model

import me.rgunny.kachi.user.domain.UserId

data class ListKeywordsQuery(
    val userId: UserId
)
