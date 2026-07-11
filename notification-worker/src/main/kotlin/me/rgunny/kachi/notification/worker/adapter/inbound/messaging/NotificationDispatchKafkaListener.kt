package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.dto.DispatchFailureClassification
import me.rgunny.kachi.notification.application.port.dto.DispatchNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.DispatchNotificationUseCase
import me.rgunny.kachi.notification.contract.NotificationDispatchEvent
import me.rgunny.kachi.notification.worker.adapter.inbound.messaging.exception.InvalidDispatchMessageException
import me.rgunny.kachi.notification.worker.adapter.inbound.messaging.exception.RetryableDispatchMessageException
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * notification.dispatch Kafka 인입 adapter.
 */
@Component
class NotificationDispatchKafkaListener(
    private val dispatchUseCase: DispatchNotificationUseCase,
    private val jsonMapper: JsonMapper,
) {

    @KafkaListener(
        topics = ["\${kachi.notification.dispatch.topic}"],
        groupId = "\${kachi.notification.dispatch.group-id}",
        containerFactory = "notificationDispatchKafkaListenerContainerFactory",
    )
    fun consume(
        @Payload payload: String,
        acknowledgment: Acknowledgment,
    ) = runBlocking {
        // 1. Kafka payload를 contract event로 역직렬화하고 core command로 변환한다.
        val command = readCommand(payload)

        // 2. 실제 상태 claim, vendor 호출, DB finalize는 core use case에 위임한다.
        val result = dispatchUseCase.dispatch(command)

        // 3. 재시도 가능한 vendor 실패는 ack하지 않고 예외로 넘겨 Kafka retry/DLT 정책을 태운다.
        if (result.failureClassification == DispatchFailureClassification.RETRYABLE) {
            throw RetryableDispatchMessageException(result)
        }

        // 4. 성공, 중복 skip, non-retryable DEAD는 현재 record 처리가 끝났으므로 offset을 commit한다.
        acknowledgment.acknowledge()
        log.info(
            "notification dispatch message consumed notificationId={} status={} duplicated={} attempted={} classification={}",
            result.notificationId.id,
            result.status,
            result.duplicated,
            result.dispatchAttempted,
            result.failureClassification,
        )
    }

    private fun readCommand(payload: String): DispatchNotificationCommand {
        return try {
            val dispatchEvent = jsonMapper.readValue(payload, NotificationDispatchEvent::class.java)
            NotificationDispatchMessageMapper.toCommand(dispatchEvent)
        } catch (exception: Exception) {
            throw InvalidDispatchMessageException("invalid notification dispatch payload", exception)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(NotificationDispatchKafkaListener::class.java)
    }
}
