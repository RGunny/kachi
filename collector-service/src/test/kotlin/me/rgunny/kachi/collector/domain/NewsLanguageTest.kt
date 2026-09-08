package me.rgunny.kachi.collector.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NewsLanguage")
class NewsLanguageTest {

    @Nested
    @DisplayName("of()")
    inner class Of {

        @Test
        @DisplayName("소문자 기본 부호만 남긴다")
        fun keepPrimarySubtag() {
            assertEquals("ko", NewsLanguage.of(" KO ").value)
            assertEquals("en", NewsLanguage.of("en-US").value)
            assertEquals("en", NewsLanguage.of("en_GB").value)
        }

        @Test
        @DisplayName("영문 2~3자가 아니면 거부한다")
        fun rejectInvalidCode() {
            assertFailsWith<IllegalArgumentException> { NewsLanguage.of("korean") }
            assertFailsWith<IllegalArgumentException> { NewsLanguage.of("k") }
            assertFailsWith<IllegalArgumentException> { NewsLanguage.of("한국어") }
            assertFailsWith<IllegalArgumentException> { NewsLanguage.of(" ") }
        }
    }
}
