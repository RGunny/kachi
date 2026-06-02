package me.rgunny.kachi.collector.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("CollectedKeyword")
class CollectedKeywordTest {

    @Nested
    @DisplayName("of()")
    inner class Of {

        @Test
        @DisplayName("수집 키워드는 앞뒤 공백을 제거한다")
        fun trimKeyword() {
            val keyword = CollectedKeyword.of("  NVIDIA  ")

            assertEquals("NVIDIA", keyword.value)
        }

        @Test
        @DisplayName("수집 키워드는 빈 값일 수 없다")
        fun rejectBlankKeyword() {
            assertFailsWith<IllegalArgumentException> {
                CollectedKeyword.of("   ")
            }
        }
    }
}
