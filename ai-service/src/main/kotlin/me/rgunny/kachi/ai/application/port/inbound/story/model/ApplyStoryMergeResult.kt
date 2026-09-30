package me.rgunny.kachi.ai.application.port.inbound.story.model

/**
 * story 병합 반영의 결과.
 *
 * [replayed]는 병합이 이미 반영되어 있었다는 뜻이다.
 * [movedPendingCount]는 흡수한 story로 옮겨진 미요약 기사 수다.
 * [summary]는 병합 후 미요약 수가 즉시 트리거를 충족해 실행된 요약의 결과이고, 트리거가 없었으면 null이다.
 */
data class ApplyStoryMergeResult(
    val replayed: Boolean,
    val movedPendingCount: Int,
    val summary: SummarizeStoryResult?
)
