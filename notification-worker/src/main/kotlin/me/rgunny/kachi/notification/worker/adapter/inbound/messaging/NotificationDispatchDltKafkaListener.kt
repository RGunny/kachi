package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.inbound.PersistNotificationDltMessageUseCase
import me.rgunny.kachi.notification.worker.adapter.outbound.monitoring.NotificationWorkerMetrics
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * notification.dispatch DLT topic 인입 adapter.
 */
@Component
class NotificationDispatchDltKafkaListener(
    private val persistDltMessageUseCase: PersistNotificationDltMessageUseCase,
    private val mapper: NotificationDispatchDltMessageMapper,
    private val metrics: NotificationWorkerMetrics,
) {

    @KafkaListener(
        topics = ["\${kachi.notification.dispatch.dlt.topic}"],
        groupId = "\${kachi.notification.dispatch.dlt.group-id}",
    )
    fun consume(
        record: ConsumerRecord<String, String>,
        @Payload payload: String,
        acknowledgment: Acknowledgment,
    ) = runBlocking {
        // 1. Spring Kafka DLT header와 DLT record 메타데이터를 core command로 변환한다.
        val command = mapper.toCommand(record, payload, Instant.now())

        // 2. 원본 Kafka record 위치 기준으로 upsert해 DLT consumer 재처리 중복 저장을 막는다.
        val result = try {
            persistDltMessageUseCase.persist(command)
        } catch (exception: CancellationException) {
            // 종료 취소를 DLT 영속 실패로 기록하면 메시지 보존 실패율이 왜곡된다.
            throw exception
        } catch (exception: Exception) {
            metrics.recordDltPersistFailure()
            throw exception
        }
        metrics.recordDltPersisted()

        // 3. 저장이 끝난 뒤에만 offset을 commit한다.
        acknowledgment.acknowledge()
        log.warn(
            "notification dispatch dlt message persisted messageId={} originalTopic={} originalPartition={} originalOffset={} dltTopic={} dltPartition={} dltOffset={} status={}",
            result.messageId.id,
            command.originalTopic,
            command.originalPartition,
            command.originalOffset,
            command.dltTopic,
            command.dltPartition,
            command.dltOffset,
            result.status,
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(NotificationDispatchDltKafkaListener::class.java)
    }
}
