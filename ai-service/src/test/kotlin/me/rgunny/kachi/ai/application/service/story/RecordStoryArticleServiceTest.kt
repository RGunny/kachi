package me.rgunny.kachi.ai.application.service.story

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.StoryRecordConflictException
import me.rgunny.kachi.ai.application.port.inbound.story.model.RecordStoryArticleCommand
import me.rgunny.kachi.ai.application.port.outbound.story.model.RecordStoryArticleOutcome
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.StoryId
import me.rgunny.kachi.ai.fake.InMemoryStoryStore
import me.rgunny.kachi.ai.fake.RecordingSummarizeStoryUseCase
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RecordStoryArticleService")
class RecordStoryArticleServiceTest {

    private val store = InMemoryStoryStore()
    private val summarizer = RecordingSummarizeStoryUseCase()
    private val service = RecordStoryArticleService(
        aiStoryPersistencePort = store,
        aiStoryArticlePersistencePort = store,
        summarizeStoryUseCase = summarizer,
        policy = AiTestFixture.storySummaryPolicy(minNewArticles = 3),
        clock = AiTestFixture.CLOCK
    )

    private fun command(
        storyId: StoryId = AiTestFixture.STORY_ID,
        newsId: UUID = AiTestFixture.NEWS_ID,
        keywords: List<String> = listOf("NVIDIA"),
        articleCount: Int = 1
    ): RecordStoryArticleCommand {
        return RecordStoryArticleCommand(
            storyId = storyId,
            newsId = newsId,
            source = "GOOGLE",
            title = "기사",
            excerpt = "발췌문",
            url = "https://news.example.com/1",
            publishedAt = AiTestFixture.NOW,
            storyKeywords = keywords.map(AiKeyword::of),
            storyArticleCount = articleCount,
            attachedAt = AiTestFixture.NOW
        )
    }

    private fun newsId(index: Int): UUID = UUID.fromString("018f0000-0000-7000-8000-00000000020$index")

    @Test
    @DisplayName("모르는 story의 첫 기사는 상태를 열며 기록한다")
    fun openStoryOnFirstArticle() = runBlocking {
        val result = service.record(command())

        assertFalse(result.replayed)
        assertEquals(AiTestFixture.STORY_ID, result.storyId)
        assertEquals(1, store.stories.getValue(AiTestFixture.STORY_ID).pendingCount)
        assertEquals(1, store.articles.size)
        assertNull(result.summary)
        assertTrue(summarizer.commands.isEmpty())
    }

    @Test
    @DisplayName("이후 기사는 상태를 누적하고 미요약이 임계에 닿으면 그 자리에서 요약한다")
    fun triggerSummaryAtThreshold() = runBlocking {
        service.record(command(newsId = newsId(1)))
        service.record(command(newsId = newsId(2), articleCount = 2))
        assertTrue(summarizer.commands.isEmpty())

        service.record(command(newsId = newsId(3), articleCount = 3, keywords = listOf("NVIDIA", "GPU")))

        assertEquals(listOf(AiTestFixture.STORY_ID), summarizer.commands.map { it.storyId })
        val story = store.stories.getValue(AiTestFixture.STORY_ID)
        assertEquals(3, story.pendingCount)
        assertEquals(listOf(AiKeyword.of("NVIDIA"), AiKeyword.of("GPU")), story.keywords)
        assertEquals(3, story.articleCount)
    }

    @Test
    @DisplayName("같은 기사의 재전달은 replayed로 끝내되 트리거는 다시 평가한다")
    fun replayEvaluatesTriggerAgain() = runBlocking {
        (1..3).forEach { service.record(command(newsId = newsId(it), articleCount = it)) }
        summarizer.commands.clear()

        val result = service.record(command(newsId = newsId(3), articleCount = 3))

        assertTrue(result.replayed)
        assertEquals(1, summarizer.commands.size)
        assertEquals(3, store.articles.size)
    }

    @Test
    @DisplayName("흡수된 story로 온 늦은 기사는 흡수한 story로 귀속한다")
    fun redirectToAbsorbingStory() = runBlocking {
        store.stories[AiTestFixture.STORY_ID] = AiTestFixture.aiStory()
        store.stories[AiTestFixture.OTHER_STORY_ID] = AiStory.trackMerged(
            storyId = AiTestFixture.OTHER_STORY_ID,
            mergedInto = AiTestFixture.STORY_ID,
            now = AiTestFixture.NOW
        )

        val result = service.record(command(storyId = AiTestFixture.OTHER_STORY_ID, newsId = newsId(1)))

        assertEquals(AiTestFixture.STORY_ID, result.storyId)
        assertEquals(AiTestFixture.STORY_ID, store.articles.getValue(newsId(1)).storyId)
        assertEquals(2, store.stories.getValue(AiTestFixture.STORY_ID).pendingCount)
    }

    @Test
    @DisplayName("CAS 경합이 상한을 넘으면 예외로 올려 재전달에 맡긴다")
    fun conflictExhaustsRetries() = runBlocking {
        store.stories[AiTestFixture.STORY_ID] = AiTestFixture.aiStory()
        store.attachOutcomeOverride = RecordStoryArticleOutcome.STORY_CHANGED

        assertFailsWith<StoryRecordConflictException> {
            service.record(command(newsId = newsId(1)))
        }
    }
}
