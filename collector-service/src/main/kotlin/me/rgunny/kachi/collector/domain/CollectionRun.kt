package me.rgunny.kachi.collector.domain

import java.time.Instant

class CollectionRun private constructor(
    val id: CollectionRunId,
    val targetType: CollectionTargetType,
    val status: CollectionRunStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val requestedKeywords: Int,
    val collectedCount: Int,
    val duplicateCount: Int,
    val failureCount: Int,
    val failureReason: String?,
    val providerResults: List<ProviderCollectionResult>
) {
    companion object {

        fun start(
            targetType: CollectionTargetType,
            requestedKeywords: Int,
            startedAt: Instant
        ): CollectionRun {
            require(requestedKeywords >= 0) { "요청 키워드 수는 0 이상이어야 합니다" }

            return CollectionRun(
                id = CollectionRunId.newId(),
                targetType = targetType,
                status = CollectionRunStatus.RUNNING,
                startedAt = startedAt,
                finishedAt = null,
                requestedKeywords = requestedKeywords,
                collectedCount = 0,
                duplicateCount = 0,
                failureCount = 0,
                failureReason = null,
                providerResults = emptyList()
            )
        }

        fun restore(
            id: CollectionRunId,
            targetType: CollectionTargetType,
            status: CollectionRunStatus,
            startedAt: Instant,
            finishedAt: Instant?,
            requestedKeywords: Int,
            collectedCount: Int,
            duplicateCount: Int,
            failureCount: Int,
            failureReason: String?,
            providerResults: List<ProviderCollectionResult>
        ): CollectionRun {
            return CollectionRun(
                id = id,
                targetType = targetType,
                status = status,
                startedAt = startedAt,
                finishedAt = finishedAt,
                requestedKeywords = requestedKeywords,
                collectedCount = collectedCount,
                duplicateCount = duplicateCount,
                failureCount = failureCount,
                failureReason = failureReason,
                providerResults = providerResults
            )
        }
    }

    fun complete(
        providerResults: List<ProviderCollectionResult>,
        finishedAt: Instant
    ): CollectionRun {
        require(status == CollectionRunStatus.RUNNING) { "실행 중인 수집만 완료할 수 있습니다" }
        require(!finishedAt.isBefore(startedAt)) { "완료 시각은 시작 시각보다 이전일 수 없습니다" }

        val failedResults = providerResults.filter { it.status == ProviderCollectionStatus.FAILED }
        val completedStatus = when {
            providerResults.isEmpty() -> CollectionRunStatus.FAILED
            failedResults.isEmpty() -> CollectionRunStatus.SUCCEEDED
            failedResults.size == providerResults.size -> CollectionRunStatus.FAILED
            else -> CollectionRunStatus.PARTIALLY_FAILED
        }

        return CollectionRun(
            id = id,
            targetType = targetType,
            status = completedStatus,
            startedAt = startedAt,
            finishedAt = finishedAt,
            requestedKeywords = requestedKeywords,
            collectedCount = providerResults.sumOf { it.savedCount },
            duplicateCount = providerResults.sumOf { it.duplicateCount },
            failureCount = failedResults.size,
            failureReason = failedResults
                .mapNotNull { it.failureMessage }
                .takeIf { it.isNotEmpty() }
                ?.joinToString("; "),
            providerResults = providerResults
        )
    }
}
