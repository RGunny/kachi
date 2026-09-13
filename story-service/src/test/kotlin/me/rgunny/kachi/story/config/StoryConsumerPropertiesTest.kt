package me.rgunny.kachi.story.config

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource

/** 항목 검증과 운영 yaml 바인딩을 보는 테스트. */
@DisplayName("StoryConsumerProperties")
class StoryConsumerPropertiesTest {

    @Test
    @DisplayName("group-id·auto-offset-reset·topic·dlt topic은 비어 있을 수 없다")
    fun rejectBlankNames() {
        assertFailsWith<IllegalArgumentException> { properties(groupId = " ") }
        assertFailsWith<IllegalArgumentException> { properties(autoOffsetReset = "") }
        assertFailsWith<IllegalArgumentException> { StoryConsumerProperties.Topics(newsCollected = " ") }
        assertFailsWith<IllegalArgumentException> { StoryConsumerProperties.Dlt(topic = "") }
    }

    @Test
    @DisplayName("concurrency와 max-poll-records는 1 이상이어야 한다")
    fun rejectNonPositiveConcurrencyAndPollRecords() {
        assertFailsWith<IllegalArgumentException> { properties(concurrency = 0) }
        assertFailsWith<IllegalArgumentException> { properties(maxPollRecords = 0) }
    }

    @Test
    @DisplayName("retry는 initial-backoff 양수, max-backoff는 그 이상, multiplier는 1 이상이어야 한다")
    fun validateRetry() {
        assertFailsWith<IllegalArgumentException> { retry(initialBackoff = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { retry(initialBackoff = Duration.ofSeconds(2), maxBackoff = Duration.ofSeconds(1)) }
        assertFailsWith<IllegalArgumentException> { retry(multiplier = 0.9) }
        assertEquals(Duration.ofSeconds(1), retry(initialBackoff = Duration.ofSeconds(1), maxBackoff = Duration.ofSeconds(1)).maxBackoff)
    }

    @Test
    @DisplayName("운영 application.yaml의 kachi.story.consumer 값이 그대로 바인딩된다")
    fun bindProductionYaml() {
        val factory = YamlPropertiesFactoryBean()
        factory.setResources(ClassPathResource("application.yaml"))
        val yaml = requireNotNull(factory.getObject()) { "application.yaml을 읽지 못했습니다" }
        val source = MapConfigurationPropertySource(yaml.entries.associate { (key, value) -> key.toString() to value.toString() })

        val properties = Binder(source).bind(StoryConsumerProperties.PREFIX, StoryConsumerProperties::class.java).get()

        assertEquals("story-service", properties.groupId)
        assertEquals("earliest", properties.autoOffsetReset)
        assertEquals(1, properties.concurrency)
        assertEquals(10, properties.maxPollRecords)
        assertEquals("collector.news.collected", properties.topics.newsCollected)
        assertEquals("story.assembly.dlt", properties.dlt.topic)
        assertEquals(Duration.ofSeconds(1), properties.retry.initialBackoff)
        assertEquals(Duration.ofSeconds(30), properties.retry.maxBackoff)
        assertEquals(2.0, properties.retry.multiplier)
    }

    private fun properties(
        groupId: String = "story-service",
        autoOffsetReset: String = "earliest",
        concurrency: Int = 1,
        maxPollRecords: Int = 10
    ): StoryConsumerProperties {
        return StoryConsumerProperties(
            groupId = groupId,
            autoOffsetReset = autoOffsetReset,
            concurrency = concurrency,
            maxPollRecords = maxPollRecords,
            topics = StoryConsumerProperties.Topics(newsCollected = "collector.news.collected"),
            dlt = StoryConsumerProperties.Dlt(topic = "story.assembly.dlt"),
            retry = retry()
        )
    }

    private fun retry(
        initialBackoff: Duration = Duration.ofSeconds(1),
        maxBackoff: Duration = Duration.ofSeconds(30),
        multiplier: Double = 2.0
    ): StoryConsumerProperties.Retry {
        return StoryConsumerProperties.Retry(initialBackoff = initialBackoff, maxBackoff = maxBackoff, multiplier = multiplier)
    }
}
