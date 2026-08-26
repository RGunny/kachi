package me.rgunny.kachi.user.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("CanonicalKey")
class CanonicalKeyTest {
    @Nested
    @DisplayName("of()")
    inner class Of {
        @Test
        @DisplayName("표기가 달라도 정규화 결과가 같으면 같은 키다")
        fun equalAfterNormalization() {
            assertEquals(CanonicalKey.of("Tesla"), CanonicalKey.of(" ＴＥＳＬＡ. "))
        }

        @Test
        @DisplayName("정규화 결과가 비면 거부한다")
        fun rejectEmptyAfterNormalization() {
            assertFailsWith<IllegalArgumentException> {
                CanonicalKey.of(" ... ")
            }
        }

        @Test
        @DisplayName("정규화 결과가 100자를 초과하면 거부한다")
        fun rejectTooLong() {
            assertFailsWith<IllegalArgumentException> {
                CanonicalKey.of("a".repeat(101))
            }
        }
    }
}
