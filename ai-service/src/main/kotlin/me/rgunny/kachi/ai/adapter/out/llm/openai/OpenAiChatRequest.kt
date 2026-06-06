package me.rgunny.kachi.ai.adapter.out.llm.openai

data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val temperature: Double = 0.2,
    val max_tokens: Int = 512
)

data class OpenAiChatMessage(
    val role: String,
    val content: String
)
