package me.rgunny.kachi.collector.config

import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxEventType
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("CollectorEventsProperties")
class CollectorEventsPropertiesTest {

    @Test
    @DisplayName("topic은 비어 있을 수 없다")
    fun rejectBlankTopic() {
        assertFailsWith<IllegalArgumentException> { CollectorTestFixture.eventsProperties(newsCollectedTopic = " ") }
    }

    @Test
    @DisplayName("보존 기간은 0보다 커야 한다")
    fun rejectNonPositiveRetention() {
        assertFailsWith<IllegalArgumentException> { CollectorTestFixture.eventsProperties(retention = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { CollectorTestFixture.eventsProperties(retention = Duration.ofDays(-1)) }
    }

    @Test
    @DisplayName("기사 수집 이벤트는 설정된 topic으로 간다")
    fun pickTopicByEventType() {
        val properties = CollectorTestFixture.eventsProperties()

        assertEquals(CollectorTestFixture.EVENT_TOPIC_NEWS_COLLECTED, properties.topicOf(CollectorOutboxEventType.NEWS_COLLECTED))
    }
}
