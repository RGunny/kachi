package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import me.rgunny.kachi.notification.application.port.dto.dlt.PersistNotificationDltMessageCommand
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.Headers
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Spring Kafka DLT record를 core DLT 영속화 command로 변환한다.
 */
@Component
class NotificationDispatchDltMessageMapper {

    /**
     * Spring Kafka가 DLT publish 시 추가한 header와 실제 DLT record metadata를 command로 모은다.
     */
    fun toCommand(
        record: ConsumerRecord<String, String>,
        payload: String,
        receivedAt: Instant,
    ): PersistNotificationDltMessageCommand {
        // 1. 원본 record 위치는 DLT idempotency key로 쓰이므로 Spring Kafka DLT header를 우선 사용한다.
        val headers = record.headers()
        val originalTopic = headers.lastString(KafkaHeaders.DLT_ORIGINAL_TOPIC) ?: record.topic()
        val originalPartition = headers.lastInt(KafkaHeaders.DLT_ORIGINAL_PARTITION) ?: record.partition()
        val originalOffset = headers.lastLong(KafkaHeaders.DLT_ORIGINAL_OFFSET) ?: record.offset()
        val originalTimestamp = headers.lastLong(KafkaHeaders.DLT_ORIGINAL_TIMESTAMP)

        // 2. DLT record 자체의 위치와 예외 metadata도 함께 저장해 운영자가 broker 위치를 추적할 수 있게 한다.
        return PersistNotificationDltMessageCommand(
            originalTopic = originalTopic,
            originalPartition = originalPartition,
            originalOffset = originalOffset,
            dltTopic = record.topic(),
            dltPartition = record.partition(),
            dltOffset = record.offset(),
            consumerGroup = headers.lastString(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP),
            messageKey = record.key(),
            payload = payload,
            exceptionFqcn = headers.lastString(KafkaHeaders.DLT_EXCEPTION_FQCN),
            exceptionMessage = headers.lastString(KafkaHeaders.DLT_EXCEPTION_MESSAGE),
            failedAt = originalTimestamp?.let(Instant::ofEpochMilli) ?: Instant.ofEpochMilli(record.timestamp()),
            receivedAt = receivedAt,
        )
    }

    private fun Headers.lastString(headerName: String): String? {
        return lastHeader(headerName)?.value()
            ?.toString(StandardCharsets.UTF_8)
            ?.takeIf { it.isNotBlank() }
    }

    private fun Headers.lastInt(headerName: String): Int? {
        return lastHeader(headerName)?.value()
            ?.let { ByteBuffer.wrap(it).int }
    }

    private fun Headers.lastLong(headerName: String): Long? {
        return lastHeader(headerName)?.value()
            ?.let { ByteBuffer.wrap(it).long }
    }
}
