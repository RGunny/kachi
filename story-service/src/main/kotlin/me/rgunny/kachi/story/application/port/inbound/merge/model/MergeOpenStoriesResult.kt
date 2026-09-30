package me.rgunny.kachi.story.application.port.inbound.merge.model

/**
 * 병합 스캔 한 번의 결과.
 *
 * [conflictedCount]는 합치는 사이 어느 한쪽이 바뀌어 건너뛴 쌍 수.
 * [indexReassignFailureCount]는 합쳐졌지만 색인 이전이 실패한 쌍 수.
 */
data class MergeOpenStoriesResult(
    val scannedCount: Int,
    val mergedCount: Int,
    val conflictedCount: Int,
    val indexReassignFailureCount: Int
)
