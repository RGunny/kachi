package me.rgunny.kachi.notification.domain

import java.time.Instant

/**
 * 이벤트 1건을 라우팅한 기록 Aggregate Root.
 *
 * eventKey가 유일하므로 같은 이벤트를 두 번 받아도 job은 하나다.
 * COMPLETED면 다시 라우팅하지 않고, STARTED면 이어서 라우팅한다. 이어서 해도 알림이 중복되지 않는 것은
 * 접수 requestId가 (이벤트, 대상)의 순수 함수이기 때문이며 이 aggregate는 진행 위치를 기억하지 않는다.
 */
class RoutingJob private constructor(
    val id: RoutingJobId,
    val eventKey: String,
    val kind: RoutingJobKind,
    val keyword: String,
    val status: RoutingJobStatus,
    val targetCount: Int,
    val acceptedCount: Int,
    val duplicatedCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
) {
    companion object {

        fun start(
            eventKey: String,
            kind: RoutingJobKind,
            keyword: String,
            now: Instant,
        ): RoutingJob {
            require(eventKey.isNotBlank()) { "eventKey must not be blank" }
            require(keyword.isNotBlank()) { "keyword must not be blank" }

            return RoutingJob(
                id = RoutingJobId.newId(),
                eventKey = eventKey,
                kind = kind,
                keyword = keyword,
                status = RoutingJobStatus.STARTED,
                targetCount = 0,
                acceptedCount = 0,
                duplicatedCount = 0,
                createdAt = now,
                updatedAt = now,
                completedAt = null,
            )
        }

        fun restore(
            id: RoutingJobId,
            eventKey: String,
            kind: RoutingJobKind,
            keyword: String,
            status: RoutingJobStatus,
            targetCount: Int,
            acceptedCount: Int,
            duplicatedCount: Int,
            createdAt: Instant,
            updatedAt: Instant,
            completedAt: Instant?,
        ): RoutingJob {
            require(eventKey.isNotBlank()) { "eventKey must not be blank" }
            require(keyword.isNotBlank()) { "keyword must not be blank" }
            requireCounts(targetCount, acceptedCount, duplicatedCount)
            require((status == RoutingJobStatus.COMPLETED) == (completedAt != null)) {
                "completedAt must be present only for COMPLETED job"
            }

            return RoutingJob(
                id = id,
                eventKey = eventKey,
                kind = kind,
                keyword = keyword,
                status = status,
                targetCount = targetCount,
                acceptedCount = acceptedCount,
                duplicatedCount = duplicatedCount,
                createdAt = createdAt,
                updatedAt = updatedAt,
                completedAt = completedAt,
            )
        }

        private fun requireCounts(targetCount: Int, acceptedCount: Int, duplicatedCount: Int) {
            require(targetCount >= 0) { "targetCount must not be negative" }
            require(acceptedCount >= 0) { "acceptedCount must not be negative" }
            require(duplicatedCount >= 0) { "duplicatedCount must not be negative" }
            require(acceptedCount + duplicatedCount <= targetCount) {
                "acceptedCount + duplicatedCount must not exceed targetCount"
            }
        }
    }

    /**
     * 라우팅 완료.
     * RoutingJobStatus: [STARTED --> COMPLETED]
     */
    fun complete(
        targetCount: Int,
        acceptedCount: Int,
        duplicatedCount: Int,
        now: Instant,
    ): RoutingJob {
        requireCounts(targetCount, acceptedCount, duplicatedCount)
        check(status == RoutingJobStatus.STARTED) {
            "complete requires STARTED, current=$status"
        }

        return RoutingJob(
            id = id,
            eventKey = eventKey,
            kind = kind,
            keyword = keyword,
            status = RoutingJobStatus.COMPLETED,
            targetCount = targetCount,
            acceptedCount = acceptedCount,
            duplicatedCount = duplicatedCount,
            createdAt = createdAt,
            updatedAt = now,
            completedAt = now,
        )
    }
}
