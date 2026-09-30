package me.rgunny.kachi.ai.application.service.quarantine

import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindStoryQuarantinesQuery
import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantineStatus
import me.rgunny.kachi.ai.fake.FakeStoryQuarantinePersistencePort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("FindStoryQuarantinesService")
class FindStoryQuarantinesServiceTest {

    private val port = FakeStoryQuarantinePersistencePort()
    private val service = FindStoryQuarantinesService(storyQuarantinePersistencePort = port)

    @Test
    @DisplayName("상태 조건 없이 전체를, 상태를 주면 그 상태만 돌려준다")
    fun findByStatus() = runBlocking {
        port.quarantines[AiTestFixture.STORY_ID] = AiTestFixture.storyQuarantine(consecutiveFailures = 3)
        port.quarantines[AiTestFixture.OTHER_STORY_ID] =
            AiTestFixture.storyQuarantine(storyId = AiTestFixture.OTHER_STORY_ID, consecutiveFailures = 1)

        val all = service.find(FindStoryQuarantinesQuery(status = null))
        val quarantined = service.find(FindStoryQuarantinesQuery(status = StoryQuarantineStatus.QUARANTINED))

        assertEquals(2, all.quarantines.size)
        assertEquals(listOf(AiTestFixture.STORY_ID), quarantined.quarantines.map { it.storyId })
    }
}
