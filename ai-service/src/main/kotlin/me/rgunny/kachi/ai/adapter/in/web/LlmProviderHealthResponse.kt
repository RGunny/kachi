package me.rgunny.kachi.ai.adapter.`in`.web

data class LlmProviderHealthResponse(
    val provider: String,
    val model: String,
    val promptVersion: String,
    val expandedKeywords: List<String>,
    val inputTokens: Int,
    val outputTokens: Int
)
