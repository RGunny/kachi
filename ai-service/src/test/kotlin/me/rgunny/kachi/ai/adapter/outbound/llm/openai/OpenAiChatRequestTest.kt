package me.rgunny.kachi.ai.adapter.outbound.llm.openai

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 요청 본문 직렬화가 값 없는 필드를 싣지 않는지 보는 테스트.
 */
class OpenAiChatRequestTest {
    private val jsonMapper = JsonMapper.builder().build()

    @Test
    @DisplayName("reasoning_effort가 없으면 요청 본문에 실리지 않는다")
    fun omitReasoningEffortWhenNull() {
        val json = jsonMapper.writeValueAsString(request(reasoningEffort = null))

        assertFalse(json.contains("reasoning_effort"), json)
    }

    @Test
    @DisplayName("reasoning_effort가 있으면 그대로 실린다")
    fun includeReasoningEffortWhenSet() {
        val tree = jsonMapper.readTree(jsonMapper.writeValueAsString(request(reasoningEffort = "none")))

        assertEquals("none", tree.path("reasoning_effort").asText())
        assertEquals(256, tree.path("max_tokens").asInt())
    }

    private fun request(reasoningEffort: String?): OpenAiChatRequest {
        return OpenAiChatRequest(
            model = "test-model",
            messages = listOf(OpenAiChatMessage(role = "user", content = "hello")),
            max_tokens = 256,
            reasoning_effort = reasoningEffort
        )
    }
}
