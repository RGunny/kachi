package me.rgunny.kachi.ai.application.service.quarantine

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.StoryQuarantineNotFoundException
import me.rgunny.kachi.ai.application.exception.StoryQuarantineNotReleasableException
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseStoryQuarantineCommand
import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantineStatus
import me.rgunny.kachi.ai.fake.FakeStoryQuarantinePersistencePort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ReleaseStoryQuarantineService")
class ReleaseStoryQuarantineServiceTest {

    private val port = FakeStoryQuarantinePersistencePort()
    private val service = ReleaseStoryQuarantineService(
        storyQuarantinePersistencePort = port,
        clock = AiTestFixture.CLOCK
    )

    private val command = ReleaseStoryQuarantineCommand(storyId = AiTestFixture.STORY_ID)

    @Test
    @DisplayName("격리된 story를 해제하고 실패 누적을 0으로 되돌린다")
    fun releaseQuarantinedStory() = runBlocking {
        port.quarantines[AiTestFixture.STORY_ID] = AiTestFixture.storyQuarantine(consecutiveFailures = 3)

        val result = service.release(command)

        assertEquals(StoryQuarantineStatus.RELEASED, result.quarantine.status)
        assertEquals(0, result.quarantine.consecutiveFailures)
        assertEquals(AiTestFixture.NOW, result.releasedAt)
        assertEquals(StoryQuarantineStatus.RELEASED, port.quarantines.getValue(AiTestFixture.STORY_ID).status)
    }

    @Test
    @DisplayName("기록이 없으면 해제할 대상이 없다")
    fun rejectUnknownStory() = runBlocking {
        assertFailsWith<StoryQuarantineNotFoundException> { service.release(command) }

        Unit
    }

    @Test
    @DisplayName("격리 상태가 아닌 기록은 해제할 수 없다")
    fun rejectNotQuarantined() = runBlocking {
        port.quarantines[AiTestFixture.STORY_ID] = AiTestFixture.storyQuarantine(consecutiveFailures = 1)

        assertFailsWith<StoryQuarantineNotReleasableException> { service.release(command) }

        Unit
    }
}
