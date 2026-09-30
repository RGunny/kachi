package me.rgunny.kachi.ai.contract

import java.time.Instant

/**
 * 요약 판정이 다른 사건으로 본 기사들을 새 story로 분리해 달라고 요청하는 이벤트 계약.
 *
 * [newsIds]는 분리 대상 기사의 식별자 목록이다.
 */
data class AiStorySplitRequestedEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val storyId: String,
    val summaryId: String,
    val newsIds: List<String>,
    val requestedAt: Instant
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
