package me.rgunny.kachi.user.adapter.outbound.persistence.keyword

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.user.domain.CanonicalKey
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName

/** `keywords` 테이블. `canonical_key`는 binary collation unique라 동등성 판단이 코드의 정규화와 같다. */
@Entity
@Table(
    name = "keywords",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_keywords_canonical_key", columnNames = ["canonical_key"])
    ]
)
class KeywordJpaEntity(

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    val id: UUID,

    @Column(name = "canonical_key", nullable = false, length = 100)
    val canonicalKey: String,

    @Column(name = "display_name", nullable = false, length = 100)
    val displayName: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
) {

    companion object {
        fun from(keyword: Keyword): KeywordJpaEntity {
            return KeywordJpaEntity(
                id = keyword.id.value,
                canonicalKey = keyword.canonicalKey.value,
                displayName = keyword.displayName.value,
                createdAt = keyword.createdAt
            )
        }
    }

    fun toDomain(): Keyword {
        return Keyword.restore(
            id = KeywordId.of(id),
            canonicalKey = CanonicalKey.of(canonicalKey),
            displayName = KeywordName.of(displayName),
            createdAt = createdAt
        )
    }
}
