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
    val ollama: OpenAiProviderProperties,
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
    /**
     * chat completions 요청의 `reasoning_effort`. null이면 요청에 싣지 않는다.
     *
     * 추론(thinking) 토큰을 쓰는 모델은 그 토큰이 `max_tokens` 예산에서 빠져나가 본문이 비어 올 수 있다.
     * 요약처럼 출력 형식이 정해진 호출은 `none`으로 추론을 끈다.
     */
    val reasoningEffort: String? = null,
)

data class GeminiProviderProperties(
    val enabled: Boolean,
    val apiKey: String,
    val baseUrl: String,
    val generateContentPath: String,
    val model: String,
)
