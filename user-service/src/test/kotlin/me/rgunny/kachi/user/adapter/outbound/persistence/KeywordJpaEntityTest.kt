package me.rgunny.kachi.user.adapter.outbound.persistence

import me.rgunny.kachi.user.domain.CanonicalKey
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("KeywordJpaEntity")
class KeywordJpaEntityTest {

    @Test
    @DisplayName("도메인과 엔티티를 왕복 변환한다")
    fun roundTrip() {
        val keyword = Keyword.restore(
            id = KeywordId.newId(),
            canonicalKey = CanonicalKey.of("space-x"),
            displayName = KeywordName.of("SPACE-X"),
            createdAt = UserTestFixture.NOW
        )

        val entity = KeywordJpaEntity.from(keyword)
        val restored = entity.toDomain()

        assertEquals("space-x", entity.canonicalKey)
        assertEquals("SPACE-X", entity.displayName)
        assertEquals(keyword.id, restored.id)
        assertEquals(keyword.canonicalKey, restored.canonicalKey)
        assertEquals(keyword.displayName, restored.displayName)
        assertEquals(keyword.createdAt, restored.createdAt)
    }
}
