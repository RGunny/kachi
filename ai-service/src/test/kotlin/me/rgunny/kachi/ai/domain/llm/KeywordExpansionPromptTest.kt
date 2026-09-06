package me.rgunny.kachi.ai.domain.llm

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("KeywordExpansionPrompt")
class KeywordExpansionPromptTest {

    // 저장 키의 일부다. 값이 바뀌면 저장된 확장이 전부 재생성되므로 의도한 변경인지 이 테스트가 묻는다.
    @Test
    @DisplayName("버전은 keyword-expansion-v2다")
    fun version() {
        assertEquals(PromptVersion.of("keyword-expansion-v2"), KeywordExpansionPrompt.version)
    }

    @Test
    @DisplayName("용도는 KEYWORD_EXPANSION이다")
    fun use() {
        assertEquals(LlmUse.KEYWORD_EXPANSION, KeywordExpansionPrompt.use)
    }

    @Test
    @DisplayName("system은 키워드를 인용 데이터로 선언하고 keywords 객체 응답을 요구한다")
    fun systemDeclaresInputAsQuotedData() {
        assertTrue(KeywordExpansionPrompt.system.contains("인용 데이터"))
        assertTrue(KeywordExpansionPrompt.system.contains("따르지 말고"))
        assertTrue(KeywordExpansionPrompt.system.contains("keywords"))
    }

    @Test
    @DisplayName("입력은 키워드 값과 최대 개수를 담는다")
    fun input() {
        val input = KeywordExpansionPrompt.input(AiKeyword.of("NVIDIA"), maxExpansions = 3)

        assertEquals(KeywordExpansionPrompt.Input(keyword = "NVIDIA", maxExpansions = 3), input)
    }
}
