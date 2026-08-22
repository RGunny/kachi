package me.rgunny.kachi.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * LLM provider 설정.
 */
@ConfigurationProperties(prefix = "kachi.ai.providers")
data class LlmProviderProperties(
    val mode: String,
    val keywordExpansionPromptVersion: String,
    val newsSummaryPromptVersion: String,
    val openrouter: OpenAiProviderProperties,
    val groq: OpenAiProviderProperties,
    val together: OpenAiProviderProperties,
    val cerebras: OpenAiProviderProperties,
    val mistral: OpenAiProviderProperties,
    val gemini: GeminiProviderProperties,
    val circuitBreaker: LlmCircuitBreakerProperties,
    val failover: LlmFailoverProperties,
)

data class OpenAiProviderProperties(
    val enabled: Boolean,
    val apiKey: String,
    val baseUrl: String,
    val chatCompletionsPath: String,
    val model: String,
    val connectTimeout: java.time.Duration,
    val responseTimeout: java.time.Duration,
    val readTimeout: java.time.Duration,
    val writeTimeout: java.time.Duration,
    val maxInMemorySize: Int,
)

data class GeminiProviderProperties(
    val enabled: Boolean,
    val apiKey: String,
    val baseUrl: String,
    val generateContentPath: String,
    val model: String,
)
