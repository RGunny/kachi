package me.rgunny.kachi.user.domain

@JvmInline
value class ProviderUserId private constructor(
    val value: String
) {
    companion object {

        fun of(value: String): ProviderUserId {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "OAuth provider 사용자 ID는 빈 값일 수 없습니다" }
            require(normalized.length <= 255) { "OAuth provider 사용자 ID는 255자를 초과할 수 없습니다" }

            return ProviderUserId(normalized)
        }
    }
}
