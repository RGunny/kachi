package me.rgunny.kachi.story.adapter.outbound.outbox

import kotlin.test.assertEquals
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryEventTopics")
class StoryEventTopicsTest {

    @Test
    @DisplayName("이벤트 종류마다 설정된 topic으로 간다")
    fun pickTopicByEventType() {
        val topics = StoryTestFixture.eventTopics()

        assertEquals(StoryTestFixture.EVENT_TOPIC_ARTICLE_ATTACHED, topics.topicOf(StoryOutboxEventType.ARTICLE_ATTACHED))
        assertEquals(StoryTestFixture.EVENT_TOPIC_MERGED, topics.topicOf(StoryOutboxEventType.MERGED))
    }
}
