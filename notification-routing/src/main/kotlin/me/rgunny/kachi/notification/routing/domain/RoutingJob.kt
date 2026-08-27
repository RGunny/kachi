package me.rgunny.kachi.notification.routing.domain

import java.time.Instant

/**
 * 이벤트 1건을 라우팅한 기록 Aggregate Root.
 *
 * eventKey가 유일하므로 같은 이벤트를 두 번 받아도 job은 하나다.
 * COMPLETED면 다시 라우팅하지 않고, STARTED면 다시 발행한다. 다시 발행해도 알림이 중복되지 않는 것은
 * requestId가 (이벤트, 대상)의 순수 함수이고 접수 쪽이 그 값으로 멱등하기 때문이며, 이 aggregate는 진행 위치를 기억하지 않는다.
 * publishedCount는 접수 topic으로 발행한 건수다. 접수·중복 여부는 접수 쪽이 안다.
 */
class RoutingJob private constructor(
    val id: RoutingJobId,
    val eventKey: String,
    val kind: RoutingJobKind,
    val keyword: String,
    val status: RoutingJobStatus,
    val targetCount: Int,
    val publishedCount: Int,
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
                publishedCount = 0,
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
            publishedCount: Int,
            createdAt: Instant,
            updatedAt: Instant,
            completedAt: Instant?,
        ): RoutingJob {
            require(eventKey.isNotBlank()) { "eventKey must not be blank" }
            require(keyword.isNotBlank()) { "keyword must not be blank" }
            requireCounts(targetCount, publishedCount)
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
                publishedCount = publishedCount,
                createdAt = createdAt,
                updatedAt = updatedAt,
                completedAt = completedAt,
            )
        }

        private fun requireCounts(targetCount: Int, publishedCount: Int) {
            require(targetCount >= 0) { "targetCount must not be negative" }
            require(publishedCount >= 0) { "publishedCount must not be negative" }
            require(publishedCount <= targetCount) { "publishedCount must not exceed targetCount" }
        }
    }

    /**
     * 라우팅 완료.
     * RoutingJobStatus: [STARTED --> COMPLETED]
     */
    fun complete(
        targetCount: Int,
        publishedCount: Int,
        now: Instant,
    ): RoutingJob {
        requireCounts(targetCount, publishedCount)
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
            publishedCount = publishedCount,
            createdAt = createdAt,
            updatedAt = now,
            completedAt = now,
        )
    }
}
