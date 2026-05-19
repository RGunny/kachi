package me.rgunny.kachi.user.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("KeywordName")
class KeywordNameTest {
    @Nested
    @DisplayName("of()")
    inner class Of {
        @Test
        @DisplayName("키워드는 앞뒤 공백을 제거한다")
        fun normalizeKeywordName() {
            val name = KeywordName.of("  NVIDIA  ")

            assertEquals("NVIDIA", name.value)
        }

        @Test
        @DisplayName("키워드는 빈 값일 수 없다")
        fun rejectBlankKeywordName() {
            assertFailsWith<IllegalArgumentException> {
                KeywordName.of("   ")
            }
        }

        @Test
        @DisplayName("키워드는 100자를 초과할 수 없다")
        fun rejectTooLongKeywordName() {
            assertFailsWith<IllegalArgumentException> {
                KeywordName.of("a".repeat(101))
            }
        }
    }
}
