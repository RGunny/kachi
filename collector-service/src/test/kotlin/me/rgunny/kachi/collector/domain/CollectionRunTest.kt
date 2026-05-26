package me.rgunny.kachi.collector.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("CollectionRun")
class CollectionRunTest {

    private val startedAt = Instant.parse("2026-05-26T00:00:00Z")
    private val finishedAt = Instant.parse("2026-05-26T00:01:00Z")

    @Nested
    @DisplayName("start()")
    inner class Start {

        @Test
        @DisplayName("수집 실행은 RUNNING 상태로 시작한다")
        fun startAsRunning() {
            val run = CollectionRun.start(
                targetType = CollectionTargetType.NEWS,
                requestedKeywords = 3,
                startedAt = startedAt
            )

            assertNotNull(run.id.value)
            assertEquals(CollectionTargetType.NEWS, run.targetType)
            assertEquals(CollectionRunStatus.RUNNING, run.status)
            assertEquals(startedAt, run.startedAt)
            assertNull(run.finishedAt)
            assertEquals(3, run.requestedKeywords)
            assertEquals(0, run.collectedCount)
            assertEquals(0, run.duplicateCount)
            assertEquals(0, run.failureCount)
        }

        @Test
        @DisplayName("요청 키워드 수는 음수일 수 없다")
        fun rejectNegativeRequestedKeywords() {
            assertFailsWith<IllegalArgumentException> {
                CollectionRun.start(
                    targetType = CollectionTargetType.NEWS,
                    requestedKeywords = -1,
                    startedAt = startedAt
                )
            }
        }
    }

    @Nested
    @DisplayName("restore()")
    inner class Restore {

        @Test
        @DisplayName("수집 실행을 저장된 상태로 복원한다")
        fun restoreCollectionRun() {
            val id = CollectionRunId.of(UUID.randomUUID())
            val providerResults = listOf(success(NewsSource.GOOGLE, savedCount = 2))

            val run = CollectionRun.restore(
                id = id,
                targetType = CollectionTargetType.NEWS,
                status = CollectionRunStatus.SUCCEEDED,
                startedAt = startedAt,
                finishedAt = finishedAt,
                requestedKeywords = 1,
                collectedCount = 2,
                duplicateCount = 0,
                failureCount = 0,
                failureReason = null,
                providerResults = providerResults
            )

            assertEquals(id, run.id)
            assertEquals(CollectionRunStatus.SUCCEEDED, run.status)
            assertEquals(providerResults, run.providerResults)
        }
    }

    @Nested
    @DisplayName("complete()")
    inner class Complete {

        @Test
        @DisplayName("모든 provider가 성공하면 SUCCEEDED로 완료한다")
        fun completeAsSucceeded() {
            val run = runningRun()

            val completed = run.complete(
                providerResults = listOf(
                    success(NewsSource.GOOGLE, savedCount = 2, duplicateCount = 1),
                    success(NewsSource.NAVER, savedCount = 3)
                ),
                finishedAt = finishedAt
            )

            assertEquals(CollectionRunStatus.SUCCEEDED, completed.status)
            assertEquals(5, completed.collectedCount)
            assertEquals(1, completed.duplicateCount)
            assertEquals(0, completed.failureCount)
            assertNull(completed.failureReason)
            assertEquals(finishedAt, completed.finishedAt)
        }

        @Test
        @DisplayName("일부 provider가 실패하면 PARTIALLY_FAILED로 완료한다")
        fun completeAsPartiallyFailed() {
            val run = runningRun()

            val completed = run.complete(
                providerResults = listOf(
                    success(NewsSource.GOOGLE, savedCount = 2),
                    ProviderCollectionResult.failure(NewsSource.NAVER, "timeout")
                ),
                finishedAt = finishedAt
            )

            assertEquals(CollectionRunStatus.PARTIALLY_FAILED, completed.status)
            assertEquals(2, completed.collectedCount)
            assertEquals(1, completed.failureCount)
            assertEquals("timeout", completed.failureReason)
        }

        @Test
        @DisplayName("모든 provider가 실패하면 FAILED로 완료한다")
        fun completeAsFailed() {
            val run = runningRun()

            val completed = run.complete(
                providerResults = listOf(
                    ProviderCollectionResult.failure(NewsSource.GOOGLE, "timeout"),
                    ProviderCollectionResult.failure(NewsSource.NAVER, "quota exceeded")
                ),
                finishedAt = finishedAt
            )

            assertEquals(CollectionRunStatus.FAILED, completed.status)
            assertEquals(0, completed.collectedCount)
            assertEquals(2, completed.failureCount)
            assertEquals("timeout; quota exceeded", completed.failureReason)
        }

        @Test
        @DisplayName("provider 결과가 없으면 FAILED로 완료한다")
        fun completeEmptyResultAsFailed() {
            val run = runningRun()

            val completed = run.complete(
                providerResults = emptyList(),
                finishedAt = finishedAt
            )

            assertEquals(CollectionRunStatus.FAILED, completed.status)
            assertEquals(0, completed.failureCount)
        }

        @Test
        @DisplayName("완료된 수집 실행은 다시 완료할 수 없다")
        fun rejectCompletingCompletedRun() {
            val completed = runningRun().complete(
                providerResults = listOf(success(NewsSource.GOOGLE)),
                finishedAt = finishedAt
            )

            assertFailsWith<IllegalArgumentException> {
                completed.complete(
                    providerResults = listOf(success(NewsSource.NAVER)),
                    finishedAt = finishedAt
                )
            }
        }

        @Test
        @DisplayName("완료 시각은 시작 시각보다 이전일 수 없다")
        fun rejectFinishedAtBeforeStartedAt() {
            val run = runningRun()

            assertFailsWith<IllegalArgumentException> {
                run.complete(
                    providerResults = listOf(success(NewsSource.GOOGLE)),
                    finishedAt = Instant.parse("2026-05-25T23:59:59Z")
                )
            }
        }
    }

    private fun runningRun(): CollectionRun {
        return CollectionRun.start(
            targetType = CollectionTargetType.NEWS,
            requestedKeywords = 1,
            startedAt = startedAt
        )
    }

    private fun success(
        source: NewsSource,
        savedCount: Int = 1,
        duplicateCount: Int = 0
    ): ProviderCollectionResult {
        return ProviderCollectionResult.success(
            source = source,
            fetchedCount = savedCount + duplicateCount,
            savedCount = savedCount,
            duplicateCount = duplicateCount
        )
    }
}
