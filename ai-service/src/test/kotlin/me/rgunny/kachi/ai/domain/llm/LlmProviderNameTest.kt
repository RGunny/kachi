package me.rgunny.kachi.ai.domain.llm

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("LlmProviderName")
class LlmProviderNameTest {

    @Test
    @DisplayName("호출된 provider가 없음을 나타내는 이름은 none이다")
    fun noneSentinel() {
        assertEquals("none", LlmProviderName.NONE.value)
        assertEquals(LlmProviderName.NONE, LlmProviderName.of("none"))
    }

    @Test
    @DisplayName("앞뒤 공백을 제거한다")
    fun trimValue() {
        assertEquals("groq", LlmProviderName.of("  groq  ").value)
    }

    @Test
    @DisplayName("빈 값으로 만들 수 없다")
    fun rejectBlankValue() {
        assertFailsWith<IllegalArgumentException> {
            LlmProviderName.of("  ")
        }
    }
}
