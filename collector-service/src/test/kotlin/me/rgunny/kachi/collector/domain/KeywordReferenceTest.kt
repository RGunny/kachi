package me.rgunny.kachi.collector.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("KeywordReference")
class KeywordReferenceTest {

    @Nested
    @DisplayName("of()")
    inner class Of {

        @Test
        @DisplayName("키워드 참조값은 앞뒤 공백을 제거한다")
        fun trimReference() {
            val reference = KeywordReference.of("  keyword-1  ")

            assertEquals("keyword-1", reference.value)
        }

        @Test
        @DisplayName("키워드 참조값은 빈 값일 수 없다")
        fun rejectBlankReference() {
            assertFailsWith<IllegalArgumentException> {
                KeywordReference.of("   ")
            }
        }
    }
}
