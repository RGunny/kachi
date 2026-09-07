package me.rgunny.kachi.ai.adapter.outbound.llm.openai

import me.rgunny.kachi.ai.domain.llm.LlmRequestOptions
import me.rgunny.kachi.ai.domain.llm.ReasoningEffort
import me.rgunny.kachi.ai.domain.llm.Thinking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("OpenAiRequestOptions")
class OpenAiRequestOptionsTest {

    @Test
    @DisplayName("OMIT인 옵션은 요청에서 빠진다")
    fun omitOptions() {
        val options = OpenAiRequestOptions.from(LlmRequestOptions(ReasoningEffort.OMIT, Thinking.OMIT))

        assertNull(options.reasoningEffort)
        assertNull(options.thinking)
    }

    @ParameterizedTest
    @EnumSource(value = ReasoningEffort::class, mode = EnumSource.Mode.EXCLUDE, names = ["OMIT"])
    @DisplayName("reasoning effort는 값 이름을 소문자로 싣는다")
    fun serializeReasoningEffort(effort: ReasoningEffort) {
        val options = OpenAiRequestOptions.from(LlmRequestOptions(effort, Thinking.OMIT))

        assertEquals(effort.name.lowercase(), options.reasoningEffort)
    }

    @ParameterizedTest
    @EnumSource(value = Thinking::class, mode = EnumSource.Mode.EXCLUDE, names = ["OMIT"])
    @DisplayName("thinking은 type 필드에 값 이름을 소문자로 싣는다")
    fun serializeThinking(thinking: Thinking) {
        val options = OpenAiRequestOptions.from(LlmRequestOptions(ReasoningEffort.OMIT, thinking))

        assertEquals(OpenAiThinking(type = thinking.name.lowercase()), options.thinking)
    }
}
