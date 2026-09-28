package me.rgunny.kachi.ai.adapter.inbound.messaging

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import me.rgunny.kachi.ai.adapter.inbound.messaging.exception.InvalidStoryEventMessageException
import me.rgunny.kachi.ai.fake.RecordingRecordStoryArticleUseCase
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@DisplayName("StoryArticleAttachedKafkaListener")
class StoryArticleAttachedKafkaListenerTest {

    private val useCase = RecordingRecordStoryArticleUseCase()
    private val listener = StoryArticleAttachedKafkaListener(
        recordStoryArticleUseCase = useCase,
        jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    )

    private fun payload(schemaVersion: Int = 1): String {
        return """
            {"schemaVersion":$schemaVersion,
             "storyId":"${AiTestFixture.STORY_ID.value}",
             "newsId":"${AiTestFixture.NEWS_ID}",
             "title":"기사","excerpt":"발췌문","url":"https://news.example.com/1",
             "source":"GOOGLE","publishedAt":"2026-06-02T23:00:00Z",
             "storyKeywords":["NVIDIA"],"storyArticleCount":1,
             "attachedAt":"2026-06-03T00:00:00Z"}
        """.trimIndent()
    }

    @Test
    @DisplayName("계약 payload를 명령으로 바꿔 유스케이스에 넘긴다")
    fun consumeValidPayload() {
        listener.consume(payload())

        val command = useCase.commands.single()
        assertEquals(AiTestFixture.STORY_ID, command.storyId)
        assertEquals(AiTestFixture.NEWS_ID, command.newsId)
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
