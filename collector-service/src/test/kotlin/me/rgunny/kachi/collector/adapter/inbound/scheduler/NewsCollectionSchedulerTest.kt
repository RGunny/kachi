package me.rgunny.kachi.collector.adapter.inbound.scheduler

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.adapter.inbound.collection.NewsCollectionExecutor
import me.rgunny.kachi.collector.application.port.inbound.collection.model.CollectNewsCommand
import me.rgunny.kachi.collector.application.port.inbound.collection.CollectNewsUseCase
import me.rgunny.kachi.collector.application.port.inbound.collection.model.CollectionRunResult
import me.rgunny.kachi.collector.domain.CollectionRunId
import me.rgunny.kachi.collector.domain.CollectionRunStatus
import me.rgunny.kachi.collector.domain.CollectionTargetType
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

@DisplayName("NewsCollectionScheduler")
class NewsCollectionSchedulerTest {

    @Test
    @DisplayName("enabled=false이면 수집을 실행하지 않는다")
    fun doNotCollectWhenDisabled() = runBlocking {
        val useCase = CountingCollectNewsUseCase()
        val scheduler = NewsCollectionScheduler(
            executor = NewsCollectionExecutor(useCase, CollectorTestFixture.executionLock()),
            settings = schedulerSettings(enabled = false)
        )

        scheduler.collectNews()

        assertEquals(0, useCase.callCount)
    }

    @Test
    @DisplayName("enabled=true이면 빈 키워드 command로 수집을 실행한다")
    fun collectWhenEnabled() = runBlocking {
        val useCase = CountingCollectNewsUseCase()
        val scheduler = NewsCollectionScheduler(
            executor = NewsCollectionExecutor(useCase, CollectorTestFixture.executionLock()),
            settings = schedulerSettings(enabled = true)
        )

        scheduler.collectNews()

        assertEquals(1, useCase.callCount)
        assertEquals(emptyList(), useCase.command.keywords)
        assertEquals(emptySet(), useCase.command.sources)
    }

    @Test
    @DisplayName("수집 예외가 발생해도 scheduler 밖으로 전파하지 않는다")
    fun swallowCollectionFailure() = runBlocking {
        val useCase = FailingCollectNewsUseCase()
        val scheduler = NewsCollectionScheduler(
            executor = NewsCollectionExecutor(useCase, CollectorTestFixture.executionLock()),
            settings = schedulerSettings(enabled = true)
        )

        scheduler.collectNews()

        assertEquals(1, useCase.callCount)
    }

    private class CountingCollectNewsUseCase : CollectNewsUseCase {
        lateinit var command: CollectNewsCommand
        var callCount = 0

        override suspend fun collect(command: CollectNewsCommand): CollectionRunResult {
            this.command = command
            callCount += 1
            return result()
        }
    }

    private class FailingCollectNewsUseCase : CollectNewsUseCase {
        var callCount = 0

        override suspend fun collect(command: CollectNewsCommand): CollectionRunResult {
            delay(1)
            callCount += 1
            throw IllegalStateException("boom")
        }
    }

    private companion object {
        fun schedulerSettings(enabled: Boolean): NewsCollectionSchedulerSettings {
            return NewsCollectionSchedulerSettings(
                enabled = enabled,
                fixedDelay = Duration.ofMinutes(10),
                initialDelay = Duration.ofSeconds(30),
            )
        }

        fun result(): CollectionRunResult {
            val now = CollectorTestFixture.NOW

            return CollectionRunResult(
                id = CollectionRunId.newId(),
                targetType = CollectionTargetType.NEWS,
                status = CollectionRunStatus.SUCCEEDED,
                startedAt = now,
                finishedAt = now,
                requestedKeywords = 0,
                collectedCount = 0,
                duplicateCount = 0,
                failureCount = 0,
                failureReason = null
            )
        }
    }
}
