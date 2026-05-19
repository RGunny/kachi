package me.rgunny.kachi.user.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("Email")
class EmailTest {

    @Nested
    @DisplayName("of()")
    inner class Of {

        @Test
        @DisplayName("이메일은 앞뒤 공백을 제거하고 소문자로 정규화한다")
        fun normalizeEmail() {
            val email = Email.of("  USER@Example.COM  ")

            assertEquals("user@example.com", email.value)
        }

        @Test
        @DisplayName("이메일은 빈 값일 수 없다")
        fun rejectBlankEmail() {
            assertFailsWith<IllegalArgumentException> {
                Email.of("   ")
            }
        }

        @Test
        @DisplayName("이메일은 `@`를 포함해야 한다")
        fun rejectEmailWithoutAtSign() {
            assertFailsWith<IllegalArgumentException> {
                Email.of("not-email")
            }
        }

        @Test
        @DisplayName("이메일은 255자를 초과할 수 없다")
        fun rejectTooLongEmail() {
            assertFailsWith<IllegalArgumentException> {
                Email.of("${"a".repeat(246)}@example.com")
            }
        }
    }
}
