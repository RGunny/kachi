package me.rgunny.kachi.ai.application.port.outbound.llm.model

/**
 * story 요약 입력으로 사용할 직전 버전 요약.
 */
data class PreviousStorySummary(
    val title: String,
    val content: String
)
