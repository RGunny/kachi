package me.rgunny.kachi.notification.domain

import me.rgunny.kachi.notification.fixture.NotificationTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("RoutingJob")
class RoutingJobTest {
    private val now = NOW

    @Test
    @DisplayName("start는 STARTED이고 카운트가 0이다")
    fun start() {
        val job = RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", now)

        assertEquals(RoutingJobStatus.STARTED, job.status)
        assertEquals(0, job.targetCount)
        assertEquals(0, job.acceptedCount)
        assertEquals(0, job.duplicatedCount)
        assertEquals(now, job.createdAt)
        assertEquals(now, job.updatedAt)
        assertNull(job.completedAt)

        assertFailsWith<IllegalArgumentException> { RoutingJob.start(" ", RoutingJobKind.SUMMARY, "tesla", now) }
        assertFailsWith<IllegalArgumentException> { RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "", now) }
    }

    @Test
    @DisplayName("complete는 카운트와 completedAt을 기록한다")
    fun complete() {
        val completedAt = now.plusSeconds(5)
        val job = RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", now)

        val completed = job.complete(targetCount = 3, acceptedCount = 2, duplicatedCount = 1, now = completedAt)

        assertEquals(RoutingJobStatus.COMPLETED, completed.status)
        assertEquals(3, completed.targetCount)
        assertEquals(2, completed.acceptedCount)
        assertEquals(1, completed.duplicatedCount)
        assertEquals(completedAt, completed.updatedAt)
        assertEquals(completedAt, completed.completedAt)
        assertEquals(job.id, completed.id)
        assertEquals(RoutingJobStatus.STARTED, job.status)
    }

    @Test
    @DisplayName("COMPLETED는 다시 complete할 수 없다")
    fun rejectCompleteTwice() {
        val completed = RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", now)
            .complete(1, 1, 0, now.plusSeconds(1))

        assertFailsWith<IllegalStateException> { completed.complete(1, 1, 0, now.plusSeconds(2)) }
    }

    @ParameterizedTest
    @CsvSource("-1, 0, 0", "1, -1, 0", "1, 0, -1", "2, 2, 1")
    @DisplayName("카운트가 음수이거나 합이 대상 수를 넘으면 complete할 수 없다")
    fun rejectInvalidCounts(targetCount: Int, acceptedCount: Int, duplicatedCount: Int) {
        val job = RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", now)

        assertFailsWith<IllegalArgumentException> {
            job.complete(targetCount, acceptedCount, duplicatedCount, now.plusSeconds(1))
        }
    }

    @Test
    @DisplayName("restore는 COMPLETED와 completedAt의 짝을 검사한다")
    fun restoreChecksCompletedAt() {
        assertFailsWith<IllegalArgumentException> { restore(RoutingJobStatus.COMPLETED, completedAt = null) }
        assertFailsWith<IllegalArgumentException> { restore(RoutingJobStatus.STARTED, completedAt = now) }
        assertEquals(RoutingJobStatus.COMPLETED, restore(RoutingJobStatus.COMPLETED, completedAt = now).status)
    }

    private fun restore(status: RoutingJobStatus, completedAt: java.time.Instant?): RoutingJob {
        return RoutingJob.restore(
            id = RoutingJobId.newId(),
            eventKey = "summary-1",
            kind = RoutingJobKind.SUMMARY,
            keyword = "tesla",
            status = status,
            targetCount = 0,
            acceptedCount = 0,
            duplicatedCount = 0,
            createdAt = now,
            updatedAt = now,
            completedAt = completedAt,
        )
    }
}
