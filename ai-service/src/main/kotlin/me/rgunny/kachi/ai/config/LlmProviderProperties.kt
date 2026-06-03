package me.rgunny.kachi.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * LLM provider 설정.
 */
@ConfigurationProperties(prefix = "kachi.ai.providers")
data class LlmProviderProperties(
    val mode: String = LlmProviderMode.SINGLE_RANDOM.value,
    val keywordExpansionPromptVersion: String = "keyword-expansion-v1",
    val openrouter: OpenAiProviderProperties = OpenAiProviderProperties(),
    val groq: OpenAiProviderProperties = OpenAiProviderProperties(),
    val together: OpenAiProviderProperties = OpenAiProviderProperties(),
    val cerebras: OpenAiProviderProperties = OpenAiProviderProperties(),
    val mistral: OpenAiProviderProperties = OpenAiProviderProperties(),
    val gemini: GeminiProviderProperties = GeminiProviderProperties()
)

data class OpenAiProviderProperties(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val baseUrl: String = "",
    val chatCompletionsPath: String = "/chat/completions",
    val model: String = "",
    val connectTimeout: java.time.Duration = java.time.Duration.ofSeconds(2),
    val responseTimeout: java.time.Duration = java.time.Duration.ofSeconds(10),
    val readTimeout: java.time.Duration = java.time.Duration.ofSeconds(10),
    val writeTimeout: java.time.Duration = java.time.Duration.ofSeconds(10),
    val maxInMemorySize: Int = 524288
)

data class GeminiProviderProperties(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val baseUrl: String = "",
    val generateContentPath: String = "/v1beta/models/{model}:generateContent",
    val model: String = ""
)
