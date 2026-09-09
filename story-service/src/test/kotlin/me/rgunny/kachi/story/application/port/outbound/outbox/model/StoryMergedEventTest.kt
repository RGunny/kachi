package me.rgunny.kachi.story.application.port.outbound.outbox.model

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import me.rgunny.kachi.story.fixture.StoryTestFixture.article
import me.rgunny.kachi.story.fixture.StoryTestFixture.story
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryMergedEvent")
class StoryMergedEventTest {
    private val target = story()
    private val other = story(article(newsId = NewsId.of(UUID.randomUUID()), storyId = StoryId.of(UUID.randomUUID())))

    @Test
    @DisplayName("흡수한 story가 파티션 키이고 흡수된 story가 이벤트 키에 앞선다")
    fun keysFromBothStories() {
        val event = StoryMergedEvent.from(other.mergeInto(target, NOW))

        assertEquals(StoryOutboxEventType.MERGED, event.type)
        assertEquals(target.id.value, event.storyId)
        assertEquals(other.id.value, event.mergedStoryId)
        assertEquals(NOW, event.mergedAt)
        assertEquals("${other.id.value}>${target.id.value}", event.eventKey)
        assertEquals(target.id.value.toString(), event.partitionKey)
    }

    @Test
    @DisplayName("흡수되지 않은 story로는 만들 수 없다")
    fun rejectNotMerged() {
        assertFailsWith<IllegalArgumentException> { StoryMergedEvent.from(other) }
        assertFailsWith<IllegalArgumentException> { StoryMergedEvent.from(other.close(NOW)) }
    }
}
