package me.rgunny.kachi.ai.config

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.rgunny.kachi.ai.adapter.inbound.messaging.exception.InvalidStoryEventMessageException
import me.rgunny.kachi.ai.application.exception.StoryRecordConflictException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOff

/**
 * story 이벤트 listener의 error handler 조립을 보는 테스트.
 *
 * 메시지 결함은 재시도 없이 DLT, 그 밖의 실패는 상한 없는 backoff라는 분류가 여기서 정해진다.
 */
@DisplayName("StoryEventConsumerKafkaConfig")
class StoryEventConsumerKafkaConfigTest {
    private val config = StoryEventConsumerKafkaConfig()

    @Test
    @DisplayName("listener factory는 레코드마다 offset을 커밋한다")
    fun buildFactoryWithRecordAck() {
        val factory = config.storyEventKafkaListenerContainerFactory(
            consumerFactory = DefaultKafkaConsumerFactory<String, String>(emptyMap<String, Any>()),
            storyEventConsumerErrorHandler = DefaultErrorHandler()
        )

        assertEquals(ContainerProperties.AckMode.RECORD, factory.containerProperties.ackMode)
    }

    @Test
    @DisplayName("backoff는 initial에서 multiplier로 늘어 max-backoff에서 멈추고 전체 시간 상한이 없다")
    fun buildUnboundedExponentialBackOff() {
        val backOff = config.storyEventConsumerBackOff(retry())

        assertEquals(1_000, backOff.initialInterval)
        assertEquals(2.0, backOff.multiplier)
        assertEquals(30_000, backOff.maxInterval)
        assertEquals(ExponentialBackOff.DEFAULT_MAX_ELAPSED_TIME, backOff.maxElapsedTime)
        assertEquals(Long.MAX_VALUE, backOff.maxElapsedTime)
    }

    @Test
    @DisplayName("메시지 결함 예외만 재시도 불가로 분류한다")
    fun classifyOnlyInvalidMessageAsNotRetryable() {
        val handler = config.storyEventConsumerErrorHandler(
            kafkaTemplate = KafkaTemplate(DefaultKafkaProducerFactory<String, String>(emptyMap<String, Any>())),
            properties = properties()
        )

        // removeClassification 반환값: false = 재시도 불가로 등록, null = 미등록
        assertEquals(false, handler.removeClassification(InvalidStoryEventMessageException::class.java))
        assertNull(handler.removeClassification(StoryRecordConflictException::class.java))
        assertNull(handler.removeClassification(IllegalStateException::class.java))
    }

    private fun properties(): StoryEventConsumerProperties {
        return StoryEventConsumerProperties(
            groupId = "ai-service",
            autoOffsetReset = "earliest",
            concurrency = 1,
            maxPollRecords = 10,
            topics = StoryEventConsumerProperties.Topics(
                storyArticleAttached = "story.article.attached",
                storyMerged = "story.merged"
            ),
            dlt = StoryEventConsumerProperties.Dlt(topic = "ai.story.dlt"),
            retry = retry()
        )
    }

    private fun retry(): StoryEventConsumerProperties.Retry {
        return StoryEventConsumerProperties.Retry(
            initialBackoff = Duration.ofSeconds(1),
            maxBackoff = Duration.ofSeconds(30),
            multiplier = 2.0
        )
    }
}
