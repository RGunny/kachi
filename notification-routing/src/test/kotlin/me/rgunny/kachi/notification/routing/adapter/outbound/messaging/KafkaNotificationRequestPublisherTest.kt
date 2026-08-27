package me.rgunny.kachi.notification.routing.adapter.outbound.messaging

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.contract.NotificationChannel
import me.rgunny.kachi.notification.contract.NotificationRequestedEvent
import me.rgunny.kachi.notification.routing.application.port.outbound.messaging.model.NotificationRequest
import me.rgunny.kachi.notification.routing.application.port.outbound.messaging.model.NotificationRequestOrigin
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.SendResult
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("KafkaNotificationRequestPublisher")
class KafkaNotificationRequestPublisherTest {

    private val jsonMapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    @DisplayName("requestId를 key로 NotificationRequestedEvent JSON을 접수 topic에 보낸다")
    fun publish() = runBlocking {
        val template = RecordingKafkaTemplate()
        val publisher = KafkaNotificationRequestPublisher(template, jsonMapper, "notification.requested")

        publisher.publish(request())

        val record = template.sent.single()
        assertEquals("notification.requested", record.topic())
        assertEquals("sum:summary-1:u:user-1:c:SLACK", record.key())
        val event = jsonMapper.readValue(record.value(), NotificationRequestedEvent::class.java)
        assertEquals(NotificationRequestedEvent.CURRENT_SCHEMA_VERSION, event.schemaVersion)
        assertEquals("sum:summary-1:u:user-1:c:SLACK", event.requestId)
        assertEquals("notification-routing", event.requester)
        assertEquals(NotificationChannel.SLACK, event.channel)
        assertEquals("ref-1", event.recipient)
        assertEquals("summary body", event.message)
        assertEquals("summary-1", event.origin?.summaryId)
        assertEquals("tesla", event.origin?.keyword)
        assertEquals("user-1", event.origin?.userId)
    }

    @Test
    @DisplayName("broker 실패는 예외로 전파한다")
    fun propagateFailure() = runBlocking {
        val template = RecordingKafkaTemplate().also { it.failure = IllegalStateException("broker down") }
        val publisher = KafkaNotificationRequestPublisher(template, jsonMapper, "notification.requested")

        assertFailsWith<IllegalStateException> { publisher.publish(request()) }
    }

    @Test
    @DisplayName("topic이 비어 있으면 만들 수 없다")
    fun rejectBlankTopic() {
        assertFailsWith<IllegalArgumentException> {
            KafkaNotificationRequestPublisher(RecordingKafkaTemplate(), jsonMapper, " ")
        }
    }

    private fun request(): NotificationRequest {
        return NotificationRequest(
            requestId = "sum:summary-1:u:user-1:c:SLACK",
            requester = "notification-routing",
            channel = NotificationChannel.SLACK,
            recipientRef = "ref-1",
            message = "summary body",
            origin = NotificationRequestOrigin("summary-1", "tesla", "user-1"),
        )
    }

    /**
     * broker 없이 send 호출만 기록하는 KafkaTemplate.
     */
    private class RecordingKafkaTemplate : KafkaTemplate<String, String>(NoopProducerFactory()) {
        val sent = mutableListOf<ProducerRecord<String, String>>()
        var failure: Throwable? = null

        override fun send(topic: String, key: String, data: String?): CompletableFuture<SendResult<String, String>> {
            failure?.let { return CompletableFuture.failedFuture(it) }
            val record = ProducerRecord<String, String>(topic, key, data)
            sent += record
            val metadata = RecordMetadata(TopicPartition(topic, 0), 0, 0, 0, 0, 0)
            return CompletableFuture.completedFuture(SendResult(record, metadata))
        }
    }

    private class NoopProducerFactory : ProducerFactory<String, String> {
        override fun createProducer() = throw UnsupportedOperationException("not used")
    }
}
