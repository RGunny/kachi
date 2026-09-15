package me.rgunny.kachi.story.application.service.outbox

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.port.inbound.outbox.model.FindStoryOutboxesQuery
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus
import me.rgunny.kachi.story.fake.FakeStoryOutboxPersistencePort
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("FindStoryOutboxesService")
class FindStoryOutboxesServiceTest {
    private val outboxPersistencePort = FakeStoryOutboxPersistencePort()
    private val service = FindStoryOutboxesService(outboxPersistencePort)

    @ParameterizedTest
    @EnumSource(StoryOutboxStatus::class)
    @DisplayName("조회 조건의 상태와 개수를 저장소에 그대로 넘긴다")
    fun passQueryToPort(status: StoryOutboxStatus) = runBlocking {
        service.find(FindStoryOutboxesQuery(status = status, limit = 10))

        assertEquals(status to 10, outboxPersistencePort.statusCalls.single())
    }

    @Test
    @DisplayName("payload를 뺀 스냅샷으로 옮긴다")
    fun mapOutboxToSummary() = runBlocking {
        val dead = StoryTestFixture.restoredOutbox(status = StoryOutboxStatus.DEAD, retryCount = 5, lastError = "broker down")
        outboxPersistencePort.store(dead)

        val summary = service.find(FindStoryOutboxesQuery()).outboxes.single()

        assertEquals(dead.id, summary.id)
        assertEquals(dead.eventType, summary.eventType)
        assertEquals(dead.eventKey, summary.eventKey)
        assertEquals(StoryOutboxStatus.DEAD, summary.status)
        assertEquals(5, summary.retryCount)
        assertEquals("broker down", summary.lastError)
    }

    @Test
    @DisplayName("조회 조건을 지정하지 않으면 DEAD를 기본 개수만큼 읽는다")
    fun useDefaultQuery() = runBlocking {
        service.find(FindStoryOutboxesQuery())

        assertEquals(
            StoryOutboxStatus.DEAD to FindStoryOutboxesQuery.DEFAULT_LIMIT,
            outboxPersistencePort.statusCalls.single()
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [0, FindStoryOutboxesQuery.MAX_LIMIT + 1])
    @DisplayName("허용 범위를 벗어난 개수는 조회 조건을 만들 때 막는다")
    fun rejectLimitOutOfRange(limit: Int) {
        assertFailsWith<IllegalArgumentException> { FindStoryOutboxesQuery(limit = limit) }
    }
}
