package me.rgunny.kachi.user.domain

@JvmInline
value class Email private constructor(
    val value: String
) {
    companion object {
        fun of(value: String): Email {
            val normalized = value.trim().lowercase()

            require(normalized.isNotBlank()) { "이메일은 빈 값일 수 없습니다" }
            require(normalized.contains("@")) { "이메일 형식이 올바르지 않습니다" }
            require(normalized.length <= 255) { "이메일은 255자를 초과할 수 없습니다" }

            return Email(normalized)
        }
    }
}
