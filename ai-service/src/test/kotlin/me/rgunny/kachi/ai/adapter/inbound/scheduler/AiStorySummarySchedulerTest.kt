package me.rgunny.kachi.ai.adapter.inbound.scheduler

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.adapter.inbound.story.AiStorySummaryExecutor
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesCommand
import me.rgunny.kachi.ai.fake.RecordingSummarizeDueStoriesUseCase
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.config.AiStorySummarySchedulerProperties
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AiStorySummaryScheduler")
class AiStorySummarySchedulerTest {

    private val useCase = RecordingSummarizeDueStoriesUseCase()

    private fun scheduler(properties: AiStorySummarySchedulerProperties = properties()): AiStorySummaryScheduler {
        return AiStorySummaryScheduler(
            executor = AiStorySummaryExecutor(useCase, AiTestFixture.executionLock()),
            properties = properties
        )
    }

    private fun properties(
        enabled: Boolean = true,
        maxStoriesPerTick: Int = 20
    ): AiStorySummarySchedulerProperties {
        return AiStorySummarySchedulerProperties(
            enabled = enabled,
            fixedDelay = Duration.ofMinutes(5),
            initialDelay = Duration.ofMinutes(1),
            maxStoriesPerTick = maxStoriesPerTick
        )
    }

    @Test
    @DisplayName("scheduler가 비활성화되어 있으면 tick을 실행하지 않는다")
    fun skipWhenDisabled() = runBlocking {
        scheduler(properties(enabled = false)).summarizeDueStories()

        assertEquals(0, useCase.commands.size)
    }

    @Test
    @DisplayName("tick 상한을 명령으로 전달한다")
    fun passMaxStoriesPerTick() = runBlocking {
        scheduler(properties(maxStoriesPerTick = 7)).summarizeDueStories()

        assertEquals(7, useCase.commands.single().maxStories)
    }

    @Test
    @DisplayName("tick 실행이 실패해도 예외를 전파하지 않는다")
    fun swallowExecutionFailure() = runBlocking {
        useCase.failure = IllegalStateException("mongo down")

        scheduler().summarizeDueStories()

        assertEquals(1, useCase.commands.size)
    }

    @Test
    @DisplayName("tick 상한이 허용 범위를 벗어나면 설정을 만들 수 없다")
    fun rejectInvalidMaxStories() {
        assertFailsWith<IllegalArgumentException> { properties(maxStoriesPerTick = 0) }
        assertFailsWith<IllegalArgumentException> {
            properties(maxStoriesPerTick = SummarizeDueStoriesCommand.MAX_STORIES_PER_TICK + 1)
        }
    }
}
