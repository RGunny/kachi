package me.rgunny.kachi.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * LLM provider 설정.
 */
@ConfigurationProperties(prefix = "kachi.ai.providers")
data class LlmProviderProperties(
    val activeProvider: String = "openrouter",
    val keywordExpansionPromptVersion: String = "keyword-expansion-v1",
    val newsSummaryPromptVersion: String = "news-summary-v1",
    val openrouter: OpenAiCompatibleProviderProperties = OpenAiCompatibleProviderProperties(),
    val groq: OpenAiCompatibleProviderProperties = OpenAiCompatibleProviderProperties(),
    val together: OpenAiCompatibleProviderProperties = OpenAiCompatibleProviderProperties(),
    val cerebras: OpenAiCompatibleProviderProperties = OpenAiCompatibleProviderProperties(),
    val mistral: OpenAiCompatibleProviderProperties = OpenAiCompatibleProviderProperties(),
    val gemini: GeminiProviderProperties = GeminiProviderProperties()
)

data class OpenAiCompatibleProviderProperties(
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
