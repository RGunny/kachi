package me.rgunny.kachi.ai.adapter.outbound.llm.openai

import me.rgunny.kachi.ai.domain.llm.LlmRequestOptions
import me.rgunny.kachi.ai.domain.llm.ReasoningEffort
import me.rgunny.kachi.ai.domain.llm.Thinking

/**
 * 모델의 요청 옵션을 이 규격의 철자로 옮긴 것.
 *
 * `OMIT`은 null이 되어 요청에서 빠진다. 값 이름은 소문자로 싣는다.
 * 새 옵션이 [LlmRequestOptions]에 생기면 여기에 그 규격 철자를 더한다.
 */
internal data class OpenAiRequestOptions(
    val reasoningEffort: String?,
    val thinking: OpenAiThinking?
) {
    companion object {
        fun from(options: LlmRequestOptions): OpenAiRequestOptions {
            return OpenAiRequestOptions(
                reasoningEffort = reasoningEffort(options.reasoningEffort),
                thinking = thinking(options.thinking)
            )
        }

        private fun reasoningEffort(effort: ReasoningEffort): String? {
            return when (effort) {
                ReasoningEffort.OMIT -> null
                ReasoningEffort.NONE,
                ReasoningEffort.LOW,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.HIGH,
                ReasoningEffort.MAX -> effort.name.lowercase()
            }
        }

        private fun thinking(thinking: Thinking): OpenAiThinking? {
            return when (thinking) {
                Thinking.OMIT -> null
                Thinking.ENABLED,
                Thinking.DISABLED -> OpenAiThinking(type = thinking.name.lowercase())
            }
        }
    }
}
