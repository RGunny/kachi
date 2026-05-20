package me.rgunny.kachi.user.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("ProviderUserId")
class ProviderUserIdTest {

    @Nested
    @DisplayName("of()")
    inner class Of {

        @Test
        @DisplayName("앞뒤 공백을 제거하고 provider 사용자 ID를 생성한다")
        fun createProviderUserId() {
            val providerUserId = ProviderUserId.of("  google-123  ")

            assertEquals("google-123", providerUserId.value)
        }

        @Test
        @DisplayName("빈 값이면 실패한다")
        fun rejectBlankValue() {
            assertFailsWith<IllegalArgumentException> {
                ProviderUserId.of("  ")
            }
        }

        @Test
        @DisplayName("255자를 초과하면 실패한다")
        fun rejectTooLongValue() {
            assertFailsWith<IllegalArgumentException> {
                ProviderUserId.of("a".repeat(256))
            }
        }
    }
}
