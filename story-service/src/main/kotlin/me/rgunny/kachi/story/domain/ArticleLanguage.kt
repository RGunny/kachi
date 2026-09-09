package me.rgunny.kachi.story.domain

/**
 * 기사 언어.
 */
@JvmInline
value class ArticleLanguage private constructor(
    val value: String
) {
    companion object {
        private val PRIMARY_SUBTAG = Regex("[a-z]{2,3}")

        fun of(value: String): ArticleLanguage {
            val primary = value.trim().lowercase().split('-', '_').first()

            require(PRIMARY_SUBTAG.matches(primary)) { "언어 부호는 영문 2~3자여야 합니다: $value" }

            return ArticleLanguage(primary)
        }
    }
}
