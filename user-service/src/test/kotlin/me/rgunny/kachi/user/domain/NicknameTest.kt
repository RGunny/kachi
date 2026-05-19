package me.rgunny.kachi.user.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("Nickname")
class NicknameTest {

    @Nested
    @DisplayName("of()")
    inner class Of {

        @Test
        @DisplayName("닉네임은 앞뒤 공백을 제거한다")
        fun normalizeNickname() {
            val nickname = Nickname.of("  rgunny  ")

            assertEquals("rgunny", nickname.value)
        }

        @Test
        @DisplayName("닉네임은 빈 값일 수 없다")
        fun rejectBlankNickname() {
            assertFailsWith<IllegalArgumentException> {
                Nickname.of("   ")
            }
        }

        @Test
        @DisplayName("닉네임은 100자를 초과할 수 없다")
        fun rejectTooLongNickname() {
            assertFailsWith<IllegalArgumentException> {
                Nickname.of("a".repeat(101))
            }
        }
    }
}
