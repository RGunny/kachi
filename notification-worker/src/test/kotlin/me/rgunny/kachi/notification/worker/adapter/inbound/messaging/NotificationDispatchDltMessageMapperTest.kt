package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.KafkaHeaders
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.test.assertEquals

@DisplayName("NotificationDispatchDltMessageMapper")
class NotificationDispatchDltMessageMapperTest {

    private val mapper = NotificationDispatchDltMessageMapper()

    @Test
    @DisplayName("Spring Kafka DLT header를 command로 변환한다")
    fun toCommand() {
        val record = ConsumerRecord(
            "notification.dispatch.dlt",
            1,
            200,
            "key-1",
            """{"notificationId":"n1"}""",
        ).also {
            headers().forEach { header -> it.headers().add(header) }
        }

        val result = mapper.toCommand(record, record.value(), Instant.parse("2026-07-13T00:00:01Z"))

        assertEquals("notification.dispatch", result.originalTopic)
        assertEquals(0, result.originalPartition)
        assertEquals(100, result.originalOffset)
        assertEquals(Instant.parse("2026-07-13T00:00:00Z"), result.originalTimestamp)
        assertEquals("notification.dispatch.dlt", result.dltTopic)
        assertEquals(1, result.dltPartition)
        assertEquals(200, result.dltOffset)
        assertEquals("notification-worker", result.consumerGroup)
        assertEquals("key-1", result.messageKey)
        assertEquals("java.net.SocketTimeoutException", result.exceptionFqcn)
        assertEquals("timeout", result.exceptionMessage)
        assertEquals(Instant.parse("2026-07-13T00:00:01Z"), result.deadLetteredAt)
        assertEquals(Instant.parse("2026-07-13T00:00:01Z"), result.storedAt)
    }

    private fun headers(): RecordHeaders {
        val headers = RecordHeaders()
        headers.add(KafkaHeaders.DLT_ORIGINAL_TOPIC, "notification.dispatch".toByteArray(StandardCharsets.UTF_8))
        headers.add(KafkaHeaders.DLT_ORIGINAL_PARTITION, ByteBuffer.allocate(Int.SIZE_BYTES).putInt(0).array())
        headers.add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(Long.SIZE_BYTES).putLong(100).array())
        headers.add(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, "notification-worker".toByteArray(StandardCharsets.UTF_8))
        headers.add(KafkaHeaders.DLT_ORIGINAL_TIMESTAMP, ByteBuffer.allocate(Long.SIZE_BYTES).putLong(1783900800000).array())
        headers.add(KafkaHeaders.DLT_EXCEPTION_FQCN, "java.net.SocketTimeoutException".toByteArray(StandardCharsets.UTF_8))
        headers.add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "timeout".toByteArray(StandardCharsets.UTF_8))
        return headers
    }
}
