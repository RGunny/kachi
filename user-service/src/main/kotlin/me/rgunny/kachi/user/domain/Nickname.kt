package me.rgunny.kachi.user.domain

@JvmInline
value class Nickname private constructor(
    val value: String
) {
    companion object {
        fun of(value: String): Nickname {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "닉네임은 빈 값일 수 없습니다" }
            require(normalized.length <= 100) { "닉네임은 100자를 초과할 수 없습니다" }

            return Nickname(normalized)
        }
    }
}
