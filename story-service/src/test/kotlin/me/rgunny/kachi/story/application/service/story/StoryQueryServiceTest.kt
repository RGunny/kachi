package me.rgunny.kachi.story.application.service.story

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.exception.StoryOperationErrorCode
import me.rgunny.kachi.story.application.exception.StoryOperationException
import me.rgunny.kachi.story.application.port.inbound.story.model.FindStoriesQuery
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryStatus
import me.rgunny.kachi.story.fake.InMemoryStoryStore
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import me.rgunny.kachi.story.fixture.StoryTestFixture.article
import me.rgunny.kachi.story.fixture.StoryTestFixture.story
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryQueryService")
class StoryQueryServiceTest {
    private val store = InMemoryStoryStore()
    private val service = StoryQueryService(storyPersistencePort = store, storyArticlePersistencePort = store)

    @Test
    @DisplayName("목록은 조회 조건을 저장소에 그대로 넘긴다")
    fun findWithQueryConditions() = runBlocking {
        val recent = seedStory(openedAt = NOW.minus(Duration.ofHours(1)))
        seedStory(openedAt = NOW.minus(Duration.ofHours(30)))

        val result = service.find(
            FindStoriesQuery(
                status = StoryStatus.OPEN,
                openedAfter = NOW.minus(Duration.ofHours(24)),
                limit = FindStoriesQuery.DEFAULT_LIMIT
            )
        )

        assertEquals(listOf(recent), result.stories.map { it.id })
    }

    @Test
    @DisplayName("상세는 story와 붙은 순의 기사를 함께 돌려준다")
    fun getStoryWithArticles() = runBlocking {
        val storyId = seedStory(openedAt = NOW.minus(Duration.ofHours(1)))

        val detail = service.get(storyId)

        assertEquals(storyId, detail.story.id)
        assertEquals(store.articles.values.filter { it.storyId == storyId }.map { it.newsId }, detail.articles.map { it.newsId })
    }

    @Test
    @DisplayName("없는 story는 STORY_NOT_FOUND다")
    fun rejectUnknownStory() = runBlocking {
        val notFound = assertFailsWith<StoryOperationException> { service.get(StoryId.newId()) }

        assertEquals(StoryOperationErrorCode.STORY_NOT_FOUND, notFound.errorCode)
    }

    private fun seedStory(openedAt: Instant): StoryId {
        val storyId = StoryId.newId()
        val first = article(newsId = NewsId.of(UUID.randomUUID()), storyId = storyId, attachedAt = openedAt)
        store.seed(story(first, now = openedAt), first)

        return storyId
    }
}
