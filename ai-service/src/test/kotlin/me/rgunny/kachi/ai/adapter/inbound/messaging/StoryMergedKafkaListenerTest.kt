package me.rgunny.kachi.ai.adapter.inbound.messaging

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import me.rgunny.kachi.ai.adapter.inbound.messaging.exception.InvalidStoryEventMessageException
import me.rgunny.kachi.ai.fake.RecordingApplyStoryMergeUseCase
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@DisplayName("StoryMergedKafkaListener")
class StoryMergedKafkaListenerTest {

    private val useCase = RecordingApplyStoryMergeUseCase()
    private val listener = StoryMergedKafkaListener(
        applyStoryMergeUseCase = useCase,
        jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    )

    private fun payload(schemaVersion: Int = 1): String {
        return """
            {"schemaVersion":$schemaVersion,
             "storyId":"${AiTestFixture.STORY_ID.value}",
             "mergedStoryId":"${AiTestFixture.OTHER_STORY_ID.value}",
             "mergedAt":"2026-06-03T00:00:00Z"}
        """.trimIndent()
    }

    @Test
    @DisplayName("계약 payload를 명령으로 바꿔 유스케이스에 넘긴다")
    fun consumeValidPayload() {
        listener.consume(payload())

        val command = useCase.commands.single()
        assertEquals(AiTestFixture.STORY_ID, command.storyId)
        assertEquals(AiTestFixture.OTHER_STORY_ID, command.mergedStoryId)
    }

    @Test
    @DisplayName("계약에 맞지 않는 payload는 재시도 불가 예외로 바꾼다")
    fun rejectInvalidPayload() {
        assertFailsWith<InvalidStoryEventMessageException> { listener.consume("not-json") }
        assertFailsWith<InvalidStoryEventMessageException> { listener.consume(payload(schemaVersion = 9)) }
        assertTrue(useCase.commands.isEmpty())
    }

    @Test
    @DisplayName("유스케이스 실패는 그대로 던진다")
    fun rethrowUseCaseFailure() {
        useCase.failure = IllegalStateException("mongo down")

        assertFailsWith<IllegalStateException> { listener.consume(payload()) }
    }
}
