package me.rgunny.kachi.user.application.port.`in`

import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.UserId
import java.time.Instant

data class RegisterKeywordResult(
    val id: KeywordId,
    val userId: UserId,
    val name: String,
    val enabled: Boolean,
    val registeredAt: Instant
) {

    companion object {

        fun from(keyword: Keyword): RegisterKeywordResult {
            return RegisterKeywordResult(
                id = keyword.id,
                userId = keyword.userId,
                name = keyword.name.value,
                enabled = keyword.enabled,
                registeredAt = keyword.registeredAt
            )
        }
    }
}
