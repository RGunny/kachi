package me.rgunny.kachi.user.domain

import java.time.Instant

/**
 * canonical 키워드.
 *
 * 정규화 값 [canonicalKey] 하나에 행 하나이며 사용자와 무관하다.
 * 사용자별 관심은 [Subscription]이 가진다.
 * [displayName]은 최초 등록 원문이다. 이름이 다르면 다른 키워드이므로 변경 행위가 없다.
 */
class Keyword private constructor(
    val id: KeywordId,
    val canonicalKey: CanonicalKey,
    val displayName: KeywordName,
    val createdAt: Instant
) {
    companion object {

        fun create(
            displayName: KeywordName,
            createdAt: Instant
        ): Keyword {
            return Keyword(
                id = KeywordId.newId(),
                canonicalKey = CanonicalKey.of(displayName.value),
                displayName = displayName,
                createdAt = createdAt
            )
        }

        fun restore(
            id: KeywordId,
            canonicalKey: CanonicalKey,
            displayName: KeywordName,
            createdAt: Instant
        ): Keyword {
            return Keyword(
                id = id,
                canonicalKey = canonicalKey,
                displayName = displayName,
                createdAt = createdAt
            )
        }
    }
}
