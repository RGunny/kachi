package me.rgunny.kachi.ai.adapter.`in`.scheduler

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.adapter.`in`.keyword.AiKeywordExpansionExecutor
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsCommand
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsResult
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsUseCase
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("AiKeywordExpansionScheduler")
class AiKeywordExpansionSchedulerTest {
    private val clock = AiTestFixture.CLOCK

    @Test
    @DisplayName("scheduler가 비활성화되어 있으면 키워드 확장을 실행하지 않는다")
    fun skipWhenSchedulerIsDisabled() = runBlocking {
        val useCase = RecordingExpandKeywordsUseCase()
        val scheduler = schedulerOf(useCase, properties(enabled = false))

        scheduler.expandKeywords()

        assertEquals(0, useCase.invokeCount)
    }

    @Test
    @DisplayName("확장 대상 키워드를 비워 보내 활성 키워드 전체를 대상으로 삼는다")
    fun requestAllActiveKeywords() = runBlocking {
        val useCase = RecordingExpandKeywordsUseCase()
        val scheduler = schedulerOf(useCase, properties(maxExpansionsPerKeyword = 7))

        scheduler.expandKeywords()

        val command = requireNotNull(useCase.lastCommand)
        assertTrue(command.keywords.isEmpty())
        assertEquals(7, command.maxExpansionsPerKeyword)
    }

    @Test
    @DisplayName("확장 실행이 실패해도 scheduler 루프가 중단되지 않도록 예외를 전파하지 않는다")
    fun swallowExecutionFailure() = runBlocking {
        val useCase = FailingExpandKeywordsUseCase()
        val scheduler = schedulerOf(useCase, properties())

        scheduler.expandKeywords()

        assertEquals(1, useCase.invokeCount)
    }

    @Test
    @DisplayName("키워드별 최대 확장 개수가 허용 범위를 벗어나면 설정을 만들 수 없다")
    fun rejectInvalidMaxExpansions() {
        assertFailsWith<IllegalArgumentException> {
            properties(maxExpansionsPerKeyword = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            properties(maxExpansionsPerKeyword = ExpandKeywordsCommand.MAX_EXPANSIONS_PER_KEYWORD + 1)
        }
    }

    private fun schedulerOf(
        useCase: ExpandKeywordsUseCase,
        properties: AiKeywordExpansionSchedulerProperties
    ): AiKeywordExpansionScheduler {
        return AiKeywordExpansionScheduler(
            executor = AiKeywordExpansionExecutor(useCase, clock),
            properties = properties
        )
    }

    private fun properties(
        enabled: Boolean = true,
        fixedDelay: Duration = Duration.ofHours(24),
        initialDelay: Duration = Duration.ofMinutes(5),
        maxExpansionsPerKeyword: Int = ExpandKeywordsCommand.DEFAULT_MAX_EXPANSIONS_PER_KEYWORD
    ): AiKeywordExpansionSchedulerProperties {
        return AiKeywordExpansionSchedulerProperties(
            enabled = enabled,
            fixedDelay = fixedDelay,
            initialDelay = initialDelay,
            maxExpansionsPerKeyword = maxExpansionsPerKeyword
        )
    }

    private class RecordingExpandKeywordsUseCase : ExpandKeywordsUseCase {
        var invokeCount = 0
        var lastCommand: ExpandKeywordsCommand? = null

        override suspend fun expand(command: ExpandKeywordsCommand): ExpandKeywordsResult {
            invokeCount += 1
            lastCommand = command

            return ExpandKeywordsResult.from(completedRun(command.keywords.size))
        }
    }

    private class FailingExpandKeywordsUseCase : ExpandKeywordsUseCase {
        var invokeCount = 0

        override suspend fun expand(command: ExpandKeywordsCommand): ExpandKeywordsResult {
            invokeCount += 1
            throw IllegalStateException("keyword expansion failed")
        }
    }

    private companion object {
        fun completedRun(requestedKeywords: Int): AiRun {
            return AiTestFixture.completedRun(
                targetType = AiRunTargetType.KEYWORD_EXPANSION,
                requestedKeywords = requestedKeywords
            )
        }
    }
}
