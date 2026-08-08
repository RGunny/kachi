package me.rgunny.kachi.user.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "keywords",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_keywords_user_id_name", columnNames = ["user_id", "name"])
    ]
)
class KeywordJpaEntity(

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    val id: UUID,

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,

    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean,

    @Column(name = "registered_at", nullable = false)
    val registeredAt: Instant,

    @Column(name = "disabled_at")
    val disabledAt: Instant?
) {

    companion object {
        fun from(keyword: Keyword): KeywordJpaEntity {
            return KeywordJpaEntity(
                id = keyword.id.value,
                userId = keyword.userId.value,
                name = keyword.name.value,
                enabled = keyword.enabled,
                registeredAt = keyword.registeredAt,
                disabledAt = keyword.disabledAt
            )
        }
    }

    fun toDomain(): Keyword {
        return Keyword.restore(
            id = KeywordId.of(id),
            userId = UserId.of(userId),
            name = KeywordName.of(name),
            enabled = enabled,
            registeredAt = registeredAt,
            disabledAt = disabledAt
        )
    }

}
