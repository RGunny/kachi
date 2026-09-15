package me.rgunny.kachi.story.application.port.inbound.index.model

/**
 * 색인 재구축 한 번의 결과.
 *
 * [skippedClosedCount]는 CLOSED story 소속이라 색인하지 않은 기사 수.
 */
data class RebuildCandidateIndexResult(
    val scannedCount: Int,
    val indexedCount: Int,
    val skippedClosedCount: Int
)
