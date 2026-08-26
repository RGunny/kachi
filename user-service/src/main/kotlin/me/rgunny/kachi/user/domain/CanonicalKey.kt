package me.rgunny.kachi.user.domain

/**
 * 정규화된 키워드 문자열.
 *
 * 표기가 다른 원문(`Tesla`, ` TESLA `, `Ｔｅｓｌａ`)이 같은 값으로 모이는 canonical 키워드의 identity이며,
 * 구독과 수집·요약이 키워드를 맞춰 보는 join key다. 정규화 규칙은 [KeywordNormalizer]에 있다.
 */
@JvmInline
value class CanonicalKey private constructor(
    val value: String
) {
    companion object {
        const val MAX_LENGTH = 100

        fun of(raw: String): CanonicalKey {
            val normalized = KeywordNormalizer.normalize(raw)

            require(normalized.isNotEmpty()) { "정규화한 키워드는 빈 값일 수 없습니다" }
            require(normalized.length <= MAX_LENGTH) { "정규화한 키워드는 ${MAX_LENGTH}자를 초과할 수 없습니다" }

            return CanonicalKey(normalized)
        }
    }
}
