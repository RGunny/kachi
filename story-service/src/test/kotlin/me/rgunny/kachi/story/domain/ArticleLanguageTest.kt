package me.rgunny.kachi.story.domain

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ArticleLanguage")
class ArticleLanguageTest {

    @Test
    @DisplayName("소문자 기본 부호만 남긴다")
    fun keepPrimarySubtag() {
        assertEquals("ko", ArticleLanguage.of(" KO ").value)
        assertEquals("en", ArticleLanguage.of("en-US").value)
        assertEquals("en", ArticleLanguage.of("en_GB").value)
    }

    @Test
    @DisplayName("영문 2~3자가 아니면 거부한다")
    fun rejectInvalidCode() {
        assertFailsWith<IllegalArgumentException> { ArticleLanguage.of("korean") }
        assertFailsWith<IllegalArgumentException> { ArticleLanguage.of("k") }
        assertFailsWith<IllegalArgumentException> { ArticleLanguage.of(" ") }
    }
}
