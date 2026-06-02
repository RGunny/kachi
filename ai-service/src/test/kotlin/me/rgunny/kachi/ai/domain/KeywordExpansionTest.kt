package me.rgunny.kachi.ai.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("KeywordExpansion")
class KeywordExpansionTest {

    @Test
    @DisplayName("원본 키워드와 중복 확장 키워드를 제거한다")
    fun create() {
        val expansion = KeywordExpansion.create(
            keyword = AiKeyword.of("NVIDIA"),
            expandedKeywords = listOf(
                ExpandedKeyword.of("NVIDIA"),
                ExpandedKeyword.of("AI 반도체"),
                ExpandedKeyword.of("ai 반도체"),
                ExpandedKeyword.of("GPU")
            ),
            provider = provider,
            model = model,
            promptVersion = promptVersion,
            createdAt = now
        )

        assertEquals(listOf("AI 반도체", "GPU"), expansion.expandedKeywords.map { it.value })
    }

    @Test
    @DisplayName("원본 키워드를 제거한 뒤 남는 확장 키워드가 없으면 생성할 수 없다")
    fun rejectEmptyExpandedKeywords() {
        assertFailsWith<IllegalArgumentException> {
            KeywordExpansion.create(
                keyword = AiKeyword.of("NVIDIA"),
                expandedKeywords = listOf(ExpandedKeyword.of("nvidia")),
                provider = provider,
                model = model,
                promptVersion = promptVersion,
                createdAt = now
            )
        }
    }

    private companion object {
        val provider = LlmProviderName.of("openai")
        val model = LlmModelName.of("gpt-4.1-mini")
        val promptVersion = PromptVersion.of("keyword-expansion-v1")
        val now = Instant.parse("2026-06-02T00:00:00Z")
    }
}
