package me.rgunny.kachi.ai.application.service.story

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.inbound.story.model.FindStorySummariesQuery
import me.rgunny.kachi.ai.fake.InMemoryStoryStore
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("FindStorySummariesService")
class FindStorySummariesServiceTest {

    private val store = InMemoryStoryStore()
    private val service = FindStorySummariesService(storySummaryPersistencePort = store)

    @Test
    @DisplayName("story의 버전을 최신 순으로 상한까지 돌려준다")
    fun findLatestFirst() = runBlocking {
        store.summaries += AiTestFixture.storySummary(version = 1)
        store.summaries += AiTestFixture.storySummary(version = 2, sourceNewsCount = 2)
        store.summaries += AiTestFixture.storySummary(storyId = AiTestFixture.OTHER_STORY_ID, version = 1)

        val result = service.find(FindStorySummariesQuery(storyId = AiTestFixture.STORY_ID, limit = 1))

        assertEquals(listOf(2L), result.summaries.map { it.version })
    }

    @Test
    @DisplayName("조회 개수는 상한을 넘을 수 없다")
    fun rejectTooLargeLimit() {
        assertFailsWith<IllegalArgumentException> {
            FindStorySummariesQuery(storyId = AiTestFixture.STORY_ID, limit = FindStorySummariesQuery.MAX_LIMIT + 1)
        }
    }
}
