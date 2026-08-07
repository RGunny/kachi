package me.rgunny.kachi.ai.adapter.`in`.keyword

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.dto.keyword.ExpandKeywordsCommand
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.fake.BlockingExpandKeywordsUseCase
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@DisplayName("AiKeywordExpansionExecutor")
class AiKeywordExpansionExecutorTest {
    private val clock = AiTestFixture.CLOCK

    @Test
    @DisplayName("실행 중인 키워드 확장이 있으면 중복 요청을 건너뛴다")
    fun skipWhenKeywordExpansionIsAlreadyRunning() = runBlocking {
        val useCase = BlockingExpandKeywordsUseCase()
        val executor = AiKeywordExpansionExecutor(useCase, clock)
        val command = ExpandKeywordsCommand(
            keywords = listOf(AiKeyword.of("NVIDIA"))
        )

        val firstExecution = async {
            executor.execute(command)
        }
        useCase.started.await()

        val secondExecution = executor.execute(command)
        useCase.release.complete(Unit)

        assertIs<AiKeywordExpansionExecutionResult.AlreadyRunning>(secondExecution)
        assertEquals(AiTestFixture.NOW, secondExecution.runningExpansion.startedAt)
        assertIs<AiKeywordExpansionExecutionResult.Started>(firstExecution.await())
        assertEquals(1, useCase.executeCount)
    }

}
