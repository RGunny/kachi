package me.rgunny.kachi.ai.domain.keyword

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("AiKeyword")
class AiKeywordTest {

    @Test
    @DisplayName("앞뒤 공백을 제거해 생성한다")
    fun create() {
        val keyword = AiKeyword.of(" NVIDIA ")

        assertEquals("NVIDIA", keyword.value)
    }

    @Test
    @DisplayName("빈 값이면 생성할 수 없다")
    fun rejectBlankValue() {
        assertFailsWith<IllegalArgumentException> {
            AiKeyword.of(" ")
        }
    }
}
