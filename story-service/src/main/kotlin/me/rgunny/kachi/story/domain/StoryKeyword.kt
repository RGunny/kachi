package me.rgunny.kachi.story.domain

/**
 * story에 붙는 라우팅 속성.
 */
@JvmInline
value class StoryKeyword private constructor(
    val value: String
) {
    companion object {
        fun of(value: String): StoryKeyword {
            val trimmed = value.trim()

            require(trimmed.isNotEmpty()) { "키워드는 빈 값일 수 없습니다" }

            return StoryKeyword(trimmed)
        }
    }
}
