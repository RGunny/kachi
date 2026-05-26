package me.rgunny.kachi.user.adapter.out.persistence

import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("KeywordJpaEntity")
class KeywordJpaEntityTest {
    private val registeredAt = Instant.parse("2026-05-20T00:00:00Z")
    private val disabledAt = Instant.parse("2026-05-20T01:00:00Z")

    @Nested
    @DisplayName("from()")
    inner class From {

        @Test
        @DisplayName("Keyword 도메인을 JPA 엔티티로 변환한다")
        fun convertKeywordToJpaEntity() {
            val keyword = restoredKeyword()

            val entity = KeywordJpaEntity.from(keyword)

            assertEquals(keyword.id.value, entity.id)
            assertEquals(keyword.userId.value, entity.userId)
            assertEquals(keyword.name.value, entity.name)
            assertEquals(keyword.enabled, entity.enabled)
            assertEquals(keyword.registeredAt, entity.registeredAt)
            assertEquals(keyword.disabledAt, entity.disabledAt)
        }
    }

    @Nested
    @DisplayName("toDomain()")
    inner class ToDomain {

        @Test
        @DisplayName("JPA 엔티티를 Keyword 도메인으로 복원한다")
        fun convertJpaEntityToKeyword() {
            val entity = KeywordJpaEntity.from(restoredKeyword())

            val keyword = entity.toDomain()

            assertEquals(KeywordId.of(entity.id), keyword.id)
            assertEquals(UserId.of(entity.userId), keyword.userId)
            assertEquals(KeywordName.of(entity.name), keyword.name)
            assertEquals(entity.enabled, keyword.enabled)
            assertEquals(entity.registeredAt, keyword.registeredAt)
            assertEquals(entity.disabledAt, keyword.disabledAt)
        }
    }

    private fun restoredKeyword(): Keyword {
        return Keyword.restore(
            id = KeywordId.of(UUID.randomUUID()),
            userId = UserId.of(UUID.randomUUID()),
            name = KeywordName.of("Trump"),
            enabled = false,
            registeredAt = registeredAt,
            disabledAt = disabledAt
        )
    }
}
