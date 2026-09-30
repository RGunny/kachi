package me.rgunny.kachi.story.config

import java.time.Duration
import kotlin.test.assertFailsWith
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryEventsProperties")
class StoryEventsPropertiesTest {

    @Test
    @DisplayName("topic은 비어 있을 수 없다")
    fun rejectBlankTopic() {
        assertFailsWith<IllegalArgumentException> { StoryTestFixture.eventsProperties(articleAttachedTopic = " ") }
        assertFailsWith<IllegalArgumentException> { StoryTestFixture.eventsProperties(mergedTopic = " ") }
    }

    @Test
    @DisplayName("보존 기간은 0보다 커야 한다")
    fun rejectNonPositiveRetention() {
        assertFailsWith<IllegalArgumentException> { StoryTestFixture.eventsProperties(retention = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { StoryTestFixture.eventsProperties(retention = Duration.ofDays(-1)) }
    }
}
