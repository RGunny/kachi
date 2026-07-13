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

    fun toCommand(
        record: ConsumerRecord<String, String>,
        payload: String,
        receivedAt: Instant,
    ): PersistNotificationDltMessageCommand {
        val headers = record.headers()
        val originalTopic = headers.lastString(KafkaHeaders.DLT_ORIGINAL_TOPIC) ?: record.topic()
        val originalPartition = headers.lastInt(KafkaHeaders.DLT_ORIGINAL_PARTITION) ?: record.partition()
        val originalOffset = headers.lastLong(KafkaHeaders.DLT_ORIGINAL_OFFSET) ?: record.offset()
        val originalTimestamp = headers.lastLong(KafkaHeaders.DLT_ORIGINAL_TIMESTAMP)

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
