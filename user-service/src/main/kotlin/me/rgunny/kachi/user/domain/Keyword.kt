package me.rgunny.kachi.user.domain

import java.time.Instant

class Keyword private constructor(
    val id: KeywordId,
    val userId: UserId,
    val name: KeywordName,
    val enabled: Boolean,
    val registeredAt: Instant,
    val disabledAt: Instant?
) {
    companion object {

        fun create(
            userId: UserId,
            name: KeywordName,
            registeredAt: Instant
        ): Keyword {
            return Keyword(
                id = KeywordId.newId(),
                userId = userId,
                name = name,
                enabled = true,
                registeredAt = registeredAt,
                disabledAt = null
            )
        }

        fun restore(
            id: KeywordId,
            userId: UserId,
            name: KeywordName,
            enabled: Boolean,
            registeredAt: Instant,
            disabledAt: Instant?
        ): Keyword {
            return Keyword(
                id = id,
                userId = userId,
                name = name,
                enabled = enabled,
                registeredAt = registeredAt,
                disabledAt = disabledAt
            )
        }
    }

    fun rename(name: KeywordName): Keyword {
        return Keyword(
            id = id,
            userId = userId,
            name = name,
            enabled = enabled,
            registeredAt = registeredAt,
            disabledAt = disabledAt
        )
    }

    fun enable(): Keyword {
        return Keyword(
            id = id,
            userId = userId,
            name = name,
            enabled = true,
            registeredAt = registeredAt,
            disabledAt = null
        )
    }

    fun disable(disabledAt: Instant): Keyword {
        require(enabled) { "이미 비활성화된 키워드입니다" }

        return Keyword(
            id = id,
            userId = userId,
            name = name,
            enabled = false,
            registeredAt = registeredAt,
            disabledAt = disabledAt
        )
    }
}
