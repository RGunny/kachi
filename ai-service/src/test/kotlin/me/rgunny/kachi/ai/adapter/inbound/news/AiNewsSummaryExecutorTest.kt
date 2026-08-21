package me.rgunny.kachi.ai.adapter.inbound.news

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.inbound.news.model.SummaryWindowRequest
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.fake.BlockingSummarizeNewsUseCase
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@DisplayName("AiNewsSummaryExecutor")
class AiNewsSummaryExecutorTest {
    private val clock = AiTestFixture.CLOCK

    @Test
    @DisplayName("실행 중인 뉴스 요약이 있으면 중복 요청을 건너뛴다")
    fun skipWhenNewsSummaryIsAlreadyRunning() = runBlocking {
        val useCase = BlockingSummarizeNewsUseCase()
        val executor = AiNewsSummaryExecutor(useCase, clock)
        val command = SummarizeNewsCommand(
            keywords = listOf(AiKeyword.of("NVIDIA")),
            window = SummaryWindowRequest.Explicit(from = null, to = null)
        )

        val firstExecution = async {
            executor.execute(command)
        }
        useCase.started.await()

        val secondExecution = executor.execute(command)
        useCase.release.complete(Unit)

        assertIs<AiNewsSummaryExecutionResult.AlreadyRunning>(secondExecution)
        assertEquals(AiTestFixture.NOW, secondExecution.runningSummary.startedAt)
        assertIs<AiNewsSummaryExecutionResult.Started>(firstExecution.await())
        assertEquals(1, useCase.executeCount)
    }

}
