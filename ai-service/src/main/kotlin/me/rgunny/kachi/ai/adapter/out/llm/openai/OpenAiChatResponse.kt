package me.rgunny.kachi.ai.adapter.out.llm.openai

data class OpenAiChatResponse(
    val model: String?,
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null
)

data class OpenAiChoice(
    val message: OpenAiChoiceMessage?
)

data class OpenAiChoiceMessage(
    val content: String?
)

data class OpenAiUsage(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null
)
