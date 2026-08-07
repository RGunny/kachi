package me.rgunny.kachi.collector.adapter.`in`.web

import kotlinx.coroutines.async
import me.rgunny.kachi.collector.adapter.`in`.collection.NewsCollectionExecutor
import me.rgunny.kachi.collector.adapter.`in`.web.response.ApiResponse
import me.rgunny.kachi.collector.application.port.`in`.CollectNewsCommand
import me.rgunny.kachi.collector.application.port.`in`.CollectNewsUseCase
import me.rgunny.kachi.collector.application.port.`in`.CollectionRunResult
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.CollectionRunId
import me.rgunny.kachi.collector.domain.CollectionRunStatus
import me.rgunny.kachi.collector.domain.CollectionTargetType
import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertIs

@DisplayName("InternalCollectionController")
class InternalCollectionControllerTest {
    private val clock = CollectorTestFixture.CLOCK

    @Test
    @DisplayName("수동 뉴스 수집 요청을 command로 변환해 실행한다")
    fun collectNews() = kotlinx.coroutines.runBlocking {
        val useCase = CountingCollectNewsUseCase()
        val controller = InternalCollectionController(NewsCollectionExecutor(useCase, clock))

        val response = controller.collectNews(
            CollectNewsRequest(
                keywords = listOf(" NVIDIA ", "AI 반도체"),
                sources = setOf("google")
            )
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(CollectedKeyword.of("NVIDIA"), CollectedKeyword.of("AI 반도체")), useCase.command.keywords)
        assertEquals(setOf(NewsSource.GOOGLE), useCase.command.sources)

        val body = response.body as ApiResponse<*>
        val data = body.data as CollectionRunResponse
        assertEquals(true, body.success)
        assertEquals("SUCCEEDED", data.status)
    }

    @Test
    @DisplayName("요청 body가 없으면 전체 활성 키워드 수집 command로 실행한다")
    fun collectNewsWithEmptyBody() = kotlinx.coroutines.runBlocking {
        val useCase = CountingCollectNewsUseCase()
        val controller = InternalCollectionController(NewsCollectionExecutor(useCase, clock))

        val response = controller.collectNews(null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), useCase.command.keywords)
        assertEquals(emptySet(), useCase.command.sources)
    }

    @Test
    @DisplayName("수집 실행 중이면 409 응답을 반환한다")
    fun returnConflictWhenCollectionIsAlreadyRunning() = kotlinx.coroutines.runBlocking {
        val useCase = AlwaysSuspendingCollectNewsUseCase()
        val executor = NewsCollectionExecutor(useCase, clock)
        val controller = InternalCollectionController(executor)
        val running = async { controller.collectNews(null) }

        useCase.started.await()
        val response = controller.collectNews(null)
        useCase.complete.complete(Unit)
        running.await()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        val body = response.body as ApiResponse<*>
        assertEquals(false, body.success)
        assertEquals("COLLECTION_ALREADY_RUNNING", body.error?.code)
    }

    private class CountingCollectNewsUseCase : CollectNewsUseCase {
        lateinit var command: CollectNewsCommand

        override suspend fun collect(command: CollectNewsCommand): CollectionRunResult {
            this.command = command
            return result()
        }
    }

    private class AlwaysSuspendingCollectNewsUseCase : CollectNewsUseCase {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val complete = kotlinx.coroutines.CompletableDeferred<Unit>()

        override suspend fun collect(command: CollectNewsCommand): CollectionRunResult {
            started.complete(Unit)
            complete.await()
            return result()
        }
    }

    private companion object {
        fun result(): CollectionRunResult {
            val now = CollectorTestFixture.NOW

            return CollectionRunResult(
                id = CollectionRunId.newId(),
                targetType = CollectionTargetType.NEWS,
                status = CollectionRunStatus.SUCCEEDED,
                startedAt = now,
                finishedAt = now,
                requestedKeywords = 2,
                collectedCount = 3,
                duplicateCount = 1,
                failureCount = 0,
                failureReason = null
            )
        }
    }
}
