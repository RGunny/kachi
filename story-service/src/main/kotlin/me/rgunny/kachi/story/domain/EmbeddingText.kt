package me.rgunny.kachi.story.domain

/**
 * 임베딩과 판정기에 넣는 텍스트.
 */
@JvmInline
value class EmbeddingText private constructor(
    val value: String
) {
    companion object {
        fun of(title: String, excerpt: String): EmbeddingText {
            require(title.isNotBlank()) { "제목은 빈 값일 수 없습니다" }
            require(excerpt.isNotBlank()) { "발췌문은 빈 값일 수 없습니다" }

            return EmbeddingText("${title.trim()}\n${excerpt.trim()}")
        }
    }
}
