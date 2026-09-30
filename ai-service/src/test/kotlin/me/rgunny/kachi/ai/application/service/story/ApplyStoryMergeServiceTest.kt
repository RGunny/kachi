package me.rgunny.kachi.ai.application.service.story

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.StoryRecordConflictException
import me.rgunny.kachi.ai.application.port.inbound.story.model.ApplyStoryMergeCommand
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.story.StoryId
import me.rgunny.kachi.ai.fake.InMemoryStoryStore
import me.rgunny.kachi.ai.fake.RecordingSummarizeStoryUseCase
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("ApplyStoryMergeService")
class ApplyStoryMergeServiceTest {

    private val store = InMemoryStoryStore()
    private val summarizer = RecordingSummarizeStoryUseCase()
    private val service = ApplyStoryMergeService(
        aiStoryPersistencePort = store,
        summarizeStoryUseCase = summarizer,
        policy = AiTestFixture.storySummaryPolicy(minNewArticles = 3),
        clock = AiTestFixture.CLOCK
    )

    private val command = ApplyStoryMergeCommand(
        storyId = AiTestFixture.STORY_ID,
        mergedStoryId = AiTestFixture.OTHER_STORY_ID,
        mergedAt = AiTestFixture.NOW
    )

    private fun newsId(index: Int): UUID = UUID.fromString("018f0000-0000-7000-8000-00000000030$index")

    @Test
    @DisplayName("흡수된 story의 미요약 기사와 키워드를 흡수한 story로 옮긴다")
    fun movePendingToAbsorbingStory() = runBlocking {
        store.stories[AiTestFixture.STORY_ID] = AiTestFixture.aiStory()
        store.stories[AiTestFixture.OTHER_STORY_ID] =
            AiTestFixture.aiStory(storyId = AiTestFixture.OTHER_STORY_ID, keywords = listOf("tesla"))
        store.articles[newsId(1)] = AiTestFixture.storyArticle(newsId = newsId(1), storyId = AiTestFixture.OTHER_STORY_ID)

        val result = service.apply(command)

        assertFalse(result.replayed)
        assertEquals(1, result.movedPendingCount)
        val absorbed = store.stories.getValue(AiTestFixture.OTHER_STORY_ID)
        assertTrue(absorbed.merged)
        assertEquals(0, absorbed.pendingCount)
        val absorbing = store.stories.getValue(AiTestFixture.STORY_ID)
        assertEquals(2, absorbing.pendingCount)
        assertEquals(listOf(AiKeyword.of("NVIDIA"), AiKeyword.of("tesla")), absorbing.keywords)
        assertEquals(AiTestFixture.STORY_ID, store.articles.getValue(newsId(1)).storyId)
    }

    @Test
    @DisplayName("옮긴 뒤 미요약이 임계에 닿으면 그 자리에서 요약한다")
    fun triggerSummaryAfterMerge() = runBlocking {
        var absorbing = AiTestFixture.aiStory()
        absorbing = absorbing.accept(listOf(AiKeyword.of("NVIDIA")), 2, AiTestFixture.NOW, AiTestFixture.NOW)
        store.stories[AiTestFixture.STORY_ID] = absorbing
        store.stories[AiTestFixture.OTHER_STORY_ID] = AiTestFixture.aiStory(storyId = AiTestFixture.OTHER_STORY_ID)

        service.apply(command)

        assertEquals(listOf(AiTestFixture.STORY_ID), summarizer.commands.map { it.storyId })
    }

    @Test
    @DisplayName("모르는 story의 병합은 자리표시만 남긴다")
    fun trackUnknownAbsorbedStory() = runBlocking {
        store.stories[AiTestFixture.STORY_ID] = AiTestFixture.aiStory()

        val result = service.apply(command)

        assertFalse(result.replayed)
        assertEquals(0, result.movedPendingCount)
        val placeholder = store.stories.getValue(AiTestFixture.OTHER_STORY_ID)
        assertEquals(AiTestFixture.STORY_ID, placeholder.mergedInto)
    }

    @Test
    @DisplayName("이미 반영된 병합은 replayed로 끝낸다")
    fun replayAppliedMerge() = runBlocking {
        store.stories[AiTestFixture.STORY_ID] = AiTestFixture.aiStory()
        store.stories[AiTestFixture.OTHER_STORY_ID] =
            AiTestFixture.aiStory(storyId = AiTestFixture.OTHER_STORY_ID).mergeInto(AiTestFixture.STORY_ID, AiTestFixture.NOW)

        val result = service.apply(command)

        assertTrue(result.replayed)
    }

    @Test
    @DisplayName("흡수한 story가 또 흡수됐으면 체인의 끝으로 옮긴다")
    fun followAbsorbingChain() = runBlocking {
        val finalStoryId = StoryId.of(UUID.fromString("018f0000-0000-7000-8000-0000000000cc"))
        store.stories[finalStoryId] = AiTestFixture.aiStory(storyId = finalStoryId)
        store.stories[AiTestFixture.STORY_ID] =
            AiTestFixture.aiStory().mergeInto(finalStoryId, AiTestFixture.NOW)
        store.stories[AiTestFixture.OTHER_STORY_ID] = AiTestFixture.aiStory(storyId = AiTestFixture.OTHER_STORY_ID)

        service.apply(command)

        assertEquals(finalStoryId, store.stories.getValue(AiTestFixture.OTHER_STORY_ID).mergedInto)
        assertEquals(2, store.stories.getValue(finalStoryId).pendingCount)
    }

    @Test
    @DisplayName("CAS 경합이 상한을 넘으면 예외로 올려 재전달에 맡긴다")
    fun conflictExhaustsRetries() = runBlocking {
        store.stories[AiTestFixture.STORY_ID] = AiTestFixture.aiStory()
        store.stories[AiTestFixture.OTHER_STORY_ID] = AiTestFixture.aiStory(storyId = AiTestFixture.OTHER_STORY_ID)
        store.mergeResult = false

        assertFailsWith<StoryRecordConflictException> { service.apply(command) }
    }
}
