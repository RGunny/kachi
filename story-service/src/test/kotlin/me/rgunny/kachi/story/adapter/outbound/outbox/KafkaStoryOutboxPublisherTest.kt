package me.rgunny.kachi.story.adapter.outbound.outbox

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.exception.StoryOutboxErrorCode
import me.rgunny.kachi.story.application.exception.StoryOutboxPublishException
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType
import me.rgunny.kachi.story.fake.FakeKafkaTemplate
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.apache.kafka.common.errors.RecordTooLargeException
import org.apache.kafka.common.errors.TimeoutException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("KafkaStoryOutboxPublisher")
class KafkaStoryOutboxPublisherTest {

    private val kafkaTemplate = FakeKafkaTemplate()
    private val topics = StoryTestFixture.eventTopics()
    private val publisher = KafkaStoryOutboxPublisher(kafkaTemplate = kafkaTemplate, topics = topics)

    @ParameterizedTest
    @EnumSource(StoryOutboxEventType::class)
    @DisplayName("행의 eventType이 고른 topic으로 레코드 하나를 보낸다")
    fun sendOneRecordToTopicOfEventType(eventType: StoryOutboxEventType) = runBlocking {
        val outbox = StoryTestFixture.outbox(eventType = eventType)

        publisher.publish(outbox)

        val record = kafkaTemplate.sent.single()
        assertEquals(topics.topicOf(eventType), record.topic)
    }

    @Test
    @DisplayName("key는 partitionKey, value는 기록된 payload 원문이다")
    fun sendPartitionKeyAndPayloadAsIs() = runBlocking {
        val outbox = StoryTestFixture.outbox()

        publisher.publish(outbox)

        val record = kafkaTemplate.sent.single()
        assertEquals(StoryTestFixture.OUTBOX_PARTITION_KEY, record.key)
        assertEquals(outbox.payload, record.value)
    }

    @Test
    @DisplayName("broker 일시 장애는 재시도 대상 실패로 던진다")
    fun throwRetryableOnTransientFailure() = runBlocking {
        kafkaTemplate.failureFor = { TimeoutException("timeout") }

        val error = assertFailsWith<StoryOutboxPublishException> { publisher.publish(StoryTestFixture.outbox()) }

        assertEquals(StoryOutboxErrorCode.OUTBOX_PUBLISH_FAILED, error.errorCode)
        assertTrue(error.retryable)
    }

    @Test
    @DisplayName("같은 payload를 다시 보내도 같은 실패면 재시도하지 않는 실패로 던진다")
    fun throwNonRetryableOnRecordTooLarge() = runBlocking {
        kafkaTemplate.failureFor = { RecordTooLargeException("too large") }

        val error = assertFailsWith<StoryOutboxPublishException> { publisher.publish(StoryTestFixture.outbox()) }

        assertEquals(StoryOutboxErrorCode.OUTBOX_PUBLISH_FAILED, error.errorCode)
        assertFalse(error.retryable)
    }

    @Test
    @DisplayName("실패 메시지에 topic과 eventKey를 남긴다")
    fun describeFailureWithTopicAndEventKey() = runBlocking {
        kafkaTemplate.failureFor = { TimeoutException("timeout") }
        val outbox = StoryTestFixture.outbox()

        val error = assertFailsWith<StoryOutboxPublishException> { publisher.publish(outbox) }

        val message = error.message!!
        assertTrue(message.contains("topic=${StoryTestFixture.EVENT_TOPIC_ARTICLE_ATTACHED}"), message)
        assertTrue(message.contains("eventKey=${StoryTestFixture.OUTBOX_EVENT_KEY}"), message)
    }
}
