package me.rgunny.kachi.collector.domain

class ProviderCollectionResult private constructor(
    val source: NewsSource,
    val status: ProviderCollectionStatus,
    val fetchedCount: Int,
    val savedCount: Int,
    val duplicateCount: Int,
    val failureMessage: String?
) {
    companion object {

        fun success(
            source: NewsSource,
            fetchedCount: Int,
            savedCount: Int,
            duplicateCount: Int
        ): ProviderCollectionResult {
            require(fetchedCount >= 0) { "수집 건수는 0 이상이어야 합니다" }
            require(savedCount >= 0) { "저장 건수는 0 이상이어야 합니다" }
            require(duplicateCount >= 0) { "중복 건수는 0 이상이어야 합니다" }

            return ProviderCollectionResult(
                source = source,
                status = ProviderCollectionStatus.SUCCEEDED,
                fetchedCount = fetchedCount,
                savedCount = savedCount,
                duplicateCount = duplicateCount,
                failureMessage = null
            )
        }

        fun failure(
            source: NewsSource,
            failureMessage: String
        ): ProviderCollectionResult {
            val normalized = failureMessage.trim()
            require(normalized.isNotBlank()) { "실패 메시지는 빈 값일 수 없습니다" }

            return ProviderCollectionResult(
                source = source,
                status = ProviderCollectionStatus.FAILED,
                fetchedCount = 0,
                savedCount = 0,
                duplicateCount = 0,
                failureMessage = normalized
            )
        }
    }
}
