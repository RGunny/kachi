package me.rgunny.kachi.user.domain

import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("Keyword")
class KeywordTest {
    private val createdAt = UserTestFixture.NOW

    @Nested
    @DisplayName("create()")
    inner class Create {
        @Test
        @DisplayName("원문을 displayName으로 두고 정규화 값을 canonicalKey로 가진다")
        fun createKeywordWithCanonicalKey() {
            val keyword = Keyword.create(displayName = KeywordName.of("SPACE-X"), createdAt = createdAt)

            assertNotNull(keyword.id.value)
            assertEquals("SPACE-X", keyword.displayName.value)
            assertEquals(CanonicalKey.of("space-x"), keyword.canonicalKey)
            assertEquals(createdAt, keyword.createdAt)
        }
    }

    @Nested
    @DisplayName("restore()")
    inner class Restore {
        @Test
        @DisplayName("저장된 상태를 그대로 복원한다")
        fun restoreKeyword() {
            val id = KeywordId.of(UUID.randomUUID())

            val keyword = Keyword.restore(
                id = id,
                canonicalKey = CanonicalKey.of("tesla"),
                displayName = KeywordName.of("Tesla"),
                createdAt = createdAt
            )

            assertEquals(id, keyword.id)
            assertEquals("tesla", keyword.canonicalKey.value)
            assertEquals("Tesla", keyword.displayName.value)
        }
    }
}
