package me.rgunny.kachi.user.application.port.`in`

import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.UserId
import java.time.Instant

data class ListKeywordResult(
    val id: KeywordId,
    val userId: UserId,
    val name: String,
    val enabled: Boolean,
    val registeredAt: Instant,
    val disabledAt: Instant?
) {

    companion object {

        fun from(keyword: Keyword): ListKeywordResult {
            return ListKeywordResult(
                id = keyword.id,
                userId = keyword.userId,
                name = keyword.name.value,
                enabled = keyword.enabled,
                registeredAt = keyword.registeredAt,
                disabledAt = keyword.disabledAt
            )
        }
    }
}
