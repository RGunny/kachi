package me.rgunny.kachi.ai.application.service.story

import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.inbound.story.model.FailedStorySummaryResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.SkippedStorySummaryResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.StorySummarySkipReason
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesCommand
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.story.StoryId
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.fake.InMemoryStoryStore
import me.rgunny.kachi.ai.fake.RecordingSummarizeStoryUseCase
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SummarizeDueStoriesService")
class SummarizeDueStoriesServiceTest {

    private val store = InMemoryStoryStore()
    private val summarizer = RecordingSummarizeStoryUseCase()
    private val service = SummarizeDueStoriesService(
        aiStoryPersistencePort = store,
        summarizeStoryUseCase = summarizer,
        policy = AiTestFixture.storySummaryPolicy(maxWait = Duration.ofMinutes(60)),
        clock = AiTestFixture.CLOCK
    )

    private fun storyId(index: Int): StoryId =
        StoryId.of(UUID.fromString("018f0000-0000-7000-8000-0000000004a$index"))

    private fun seedDueStory(index: Int) {
        store.stories[storyId(index)] = AiTestFixture.aiStory(
            storyId = storyId(index),
            attachedAt = AiTestFixture.NOW.minus(Duration.ofMinutes(61)).plusSeconds(index.toLong())
        )
    }

    private fun summarizeDue(maxStories: Int = 20) = runBlocking {
        service.summarizeDue(SummarizeDueStoriesCommand(maxStories = maxStories))
    }

    @Test
    @DisplayName("maxWait를 넘긴 story만 오래된 순으로 요약한다")
    fun summarizeOnlyDueStories() {
        seedDueStory(1)
        seedDueStory(2)
        store.stories[storyId(3)] = AiTestFixture.aiStory(storyId = storyId(3), attachedAt = AiTestFixture.NOW)

        val result = summarizeDue()

        assertEquals(2, result.due)
        assertEquals(listOf(storyId(1), storyId(2)), summarizer.commands.map { it.storyId })
    }

    @Test
    @DisplayName("결과 종류를 세고 tick 상한을 지킨다")
    fun countResultsWithinLimit() {
        (1..3).forEach(::seedDueStory)
        summarizer.results += AiTestFixture.createdResult()
        summarizer.results += SkippedStorySummaryResult(StorySummarySkipReason.SUPERSEDED)

        val result = summarizeDue(maxStories = 2)

        assertEquals(2, result.due)
        assertEquals(1, result.created)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
        assertFalse(result.aborted)
    }

    @Test
    @DisplayName("story 하나의 실패가 나머지를 막지 않는다")
    fun isolateFailurePerStory() {
        (1..2).forEach(::seedDueStory)
        summarizer.results += FailedStorySummaryResult(reason = AiFailureReason.CLIENT_ERROR, quarantined = false)
        summarizer.results += AiTestFixture.createdResult()

        val result = summarizeDue()

        assertEquals(1, result.failed)
        assertEquals(1, result.created)
    }

    @Test
    @DisplayName("호출할 모델이 하나도 없으면 남은 story를 건너뛴다")
    fun abortWhenNoModelCanBeCalled() {
        (1..3).forEach(::seedDueStory)
        summarizer.failure = AiTestFixture.llmProviderException(LlmFailureCode.LLM_NOT_PERMITTED)

        val result = summarizeDue()

        assertTrue(result.aborted)
        assertEquals(1, result.failed)
        assertEquals(2, result.skipped)
        assertEquals(1, summarizer.commands.size)
    }
}
