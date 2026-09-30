package me.rgunny.kachi.ai.config

import java.time.Duration
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryEventConsumerProperties")
class StoryEventConsumerPropertiesTest {

    private fun properties(
        groupId: String = "ai-service",
        concurrency: Int = 1,
        maxPollRecords: Int = 10,
        attachedTopic: String = "story.article.attached",
        dltTopic: String = "ai.story.dlt",
        initialBackoff: Duration = Duration.ofSeconds(1),
        maxBackoff: Duration = Duration.ofSeconds(30),
        multiplier: Double = 2.0
    ): StoryEventConsumerProperties {
        return StoryEventConsumerProperties(
            groupId = groupId,
            autoOffsetReset = "earliest",
            concurrency = concurrency,
            maxPollRecords = maxPollRecords,
            topics = StoryEventConsumerProperties.Topics(
                storyArticleAttached = attachedTopic,
                storyMerged = "story.merged"
            ),
            dlt = StoryEventConsumerProperties.Dlt(topic = dltTopic),
            retry = StoryEventConsumerProperties.Retry(
                initialBackoff = initialBackoff,
                maxBackoff = maxBackoff,
                multiplier = multiplier
            )
        )
    }

    @Test
    @DisplayName("빈 식별자와 topic을 거부한다")
    fun rejectBlankValues() {
        assertFailsWith<IllegalArgumentException> { properties(groupId = " ") }
        assertFailsWith<IllegalArgumentException> { properties(attachedTopic = "") }
        assertFailsWith<IllegalArgumentException> { properties(dltTopic = " ") }
    }

    @Test
    @DisplayName("poll·동시성 하한을 거부한다")
    fun rejectNonPositiveCounts() {
        assertFailsWith<IllegalArgumentException> { properties(concurrency = 0) }
        assertFailsWith<IllegalArgumentException> { properties(maxPollRecords = 0) }
    }

    @Test
    @DisplayName("backoff 값의 관계를 검증한다")
    fun rejectInvalidBackoff() {
        assertFailsWith<IllegalArgumentException> { properties(initialBackoff = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { properties(maxBackoff = Duration.ofMillis(500)) }
        assertFailsWith<IllegalArgumentException> { properties(multiplier = 0.5) }
    }
}
