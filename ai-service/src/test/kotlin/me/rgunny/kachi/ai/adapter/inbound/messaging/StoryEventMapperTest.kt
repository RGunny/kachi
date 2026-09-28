package me.rgunny.kachi.ai.adapter.inbound.messaging

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.story.contract.StoryArticleAttachedEvent
import me.rgunny.kachi.story.contract.StoryArticleSource
import me.rgunny.kachi.story.contract.StoryMergedEvent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("StoryEventMapper")
class StoryEventMapperTest {

    private fun attachedEvent(
        schemaVersion: Int = StoryArticleAttachedEvent.CURRENT_SCHEMA_VERSION,
        newsId: String = AiTestFixture.NEWS_ID.toString()
    ): StoryArticleAttachedEvent {
        return StoryArticleAttachedEvent(
            schemaVersion = schemaVersion,
            storyId = AiTestFixture.STORY_ID.value.toString(),
            newsId = newsId,
            title = "기사 제목",
            excerpt = "발췌문",
            url = "https://news.example.com/1",
            source = StoryArticleSource.NAVER,
            publishedAt = Instant.parse("2026-06-02T23:00:00Z"),
            storyKeywords = listOf("NVIDIA", "GPU"),
            storyArticleCount = 2,
            attachedAt = AiTestFixture.NOW
        )
    }

    @Test
    @DisplayName("기사 이벤트를 기록 명령으로 옮긴다")
    fun mapAttachedEvent() {
        val command = StoryEventMapper.toRecordCommand(attachedEvent())

        assertEquals(AiTestFixture.STORY_ID, command.storyId)
        assertEquals(AiTestFixture.NEWS_ID, command.newsId)
        assertEquals("NAVER", command.source)
        assertEquals(listOf(AiKeyword.of("NVIDIA"), AiKeyword.of("GPU")), command.storyKeywords)
        assertEquals(2, command.storyArticleCount)
        assertEquals(AiTestFixture.NOW, command.attachedAt)
    }

    @ParameterizedTest
    @EnumSource(StoryArticleSource::class)
    @DisplayName("계약 source enum은 값마다 이름으로 짝지어진다")
    fun mapEverySource(source: StoryArticleSource) {
        val command = StoryEventMapper.toRecordCommand(attachedEvent().copy(source = source))

        assertEquals(source.name, command.source)
    }

    @Test
    @DisplayName("병합 이벤트를 반영 명령으로 옮긴다")
    fun mapMergedEvent() {
        val command = StoryEventMapper.toMergeCommand(
            StoryMergedEvent(
                storyId = AiTestFixture.STORY_ID.value.toString(),
                mergedStoryId = AiTestFixture.OTHER_STORY_ID.value.toString(),
                mergedAt = AiTestFixture.NOW
            )
        )

        assertEquals(AiTestFixture.STORY_ID, command.storyId)
        assertEquals(AiTestFixture.OTHER_STORY_ID, command.mergedStoryId)
        assertEquals(AiTestFixture.NOW, command.mergedAt)
    }

    @Test
    @DisplayName("모르는 schemaVersion은 거부한다")
    fun rejectUnknownSchemaVersion() {
        assertFailsWith<IllegalArgumentException> {
            StoryEventMapper.toRecordCommand(attachedEvent(schemaVersion = 2))
        }
        assertFailsWith<IllegalArgumentException> {
            StoryEventMapper.toMergeCommand(
                StoryMergedEvent(
                    schemaVersion = 2,
                    storyId = AiTestFixture.STORY_ID.value.toString(),
                    mergedStoryId = AiTestFixture.OTHER_STORY_ID.value.toString(),
                    mergedAt = AiTestFixture.NOW
                )
            )
        }
    }

    @Test
    @DisplayName("UUID가 아닌 id는 거부한다")
    fun rejectInvalidUuid() {
        assertFailsWith<IllegalArgumentException> {
            StoryEventMapper.toRecordCommand(attachedEvent(newsId = "not-a-uuid"))
        }
    }
}
