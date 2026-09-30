package me.rgunny.kachi.story.domain

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("EmbeddingText")
class EmbeddingTextTest {

    @Test
    @DisplayName("제목과 발췌문을 줄바꿈 하나로 잇는다")
    fun joinWithNewline() {
        assertEquals("제목\n발췌문", EmbeddingText.of(" 제목 ", " 발췌문 ").value)
    }

    @Test
    @DisplayName("제목이나 발췌문이 비면 거부한다")
    fun rejectBlank() {
        assertFailsWith<IllegalArgumentException> { EmbeddingText.of(" ", "발췌문") }
        assertFailsWith<IllegalArgumentException> { EmbeddingText.of("제목", "") }
    }
}
