package me.rgunny.kachi.story.domain

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryKeyword")
class StoryKeywordTest {

    @Test
    @DisplayName("앞뒤 공백만 지우고 값은 그대로 둔다")
    fun trimOnly() {
        assertEquals("NVIDIA", StoryKeyword.of(" NVIDIA ").value)
    }

    @Test
    @DisplayName("빈 값은 거부한다")
    fun rejectBlank() {
        assertFailsWith<IllegalArgumentException> { StoryKeyword.of(" ") }
    }
}
