package me.rgunny.kachi.story.adapter.outbound.outbox

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import me.rgunny.kachi.story.application.exception.StoryOutboxErrorCode
import me.rgunny.kachi.story.application.exception.StoryOutboxPublishException
import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxPublisherPort
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import org.springframework.kafka.core.KafkaTemplate

/**
 * outbox 행 하나를 Kafka 레코드 하나로 발행하는 어댑터.
 */
class KafkaStoryOutboxPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val topics: StoryEventTopics
) : StoryOutboxPublisherPort {

    override suspend fun publish(outbox: StoryOutbox) {
        val topic = topics.topicOf(outbox.eventType)

        try {
            kafkaTemplate.send(topic, outbox.partitionKey, outbox.payload).await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw StoryOutboxPublishException(
                errorCode = StoryOutboxErrorCode.OUTBOX_PUBLISH_FAILED,
                retryable = KafkaPublishFailureClassifier.isRetryable(error),
                detail = "topic=$topic, eventKey=${outbox.eventKey}",
                cause = error
            )
        }
    }
}
