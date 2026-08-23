package me.rgunny.kachi.ai.contract

import java.time.Instant

/**
 * ai-service가 뉴스 요약 생성을 알릴 때 사용하는 이벤트 계약.
 */
data class AiSummaryCreatedEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val summaryId: String,
    val keyword: String,
    val title: String,
    val content: String,
    val sentiment: AiSummarySentiment,
    val sourceNewsCount: Int,
    val provider: String,
    val model: String,
    val promptVersion: String,
    val createdAt: Instant
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
