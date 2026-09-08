package me.rgunny.kachi.collector.domain

/**
 * 기사 언어.
 *
 * ISO 639 기본 부호(`ko`, `en`)만 갖는다. `en-US`처럼 지역이 붙은 값은 앞부분만 남긴다.
 * provider가 어느 언어의 기사를 주는지는 provider 설정이 정하고, 이 값은 그것을 기사에 옮긴 것이다.
 */
@JvmInline
value class NewsLanguage private constructor(
    val value: String
) {
    companion object {
        private val PRIMARY_SUBTAG = Regex("[a-z]{2,3}")

        fun of(value: String): NewsLanguage {
            val primary = value.trim().lowercase().split('-', '_').first()

            require(PRIMARY_SUBTAG.matches(primary)) { "언어 부호는 영문 2~3자여야 합니다: $value" }

            return NewsLanguage(primary)
        }
    }
}
