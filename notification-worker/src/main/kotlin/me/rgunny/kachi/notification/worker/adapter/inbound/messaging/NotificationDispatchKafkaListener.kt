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
 * notification.dispatch Kafka 인입 adapter.s
 */
@Component
class NotificationDispatchKafkaListener(
    private val dispatchUseCase: DispatchNotificationUseCase,
    private val jsonMapper: JsonMapper,
) {

    @KafkaListener(
        topics = ["\${kachi.notification.worker.dispatch.topic}"],
        groupId = "\${kachi.notification.worker.dispatch.group-id}",
        containerFactory = "notificationDispatchKafkaListenerContainerFactory",
    )
    fun consume(
        @Payload payload: String,
        acknowledgment: Acknowledgment,
    ) = runBlocking {
        val command = readCommand(payload)
        val result = dispatchUseCase.dispatch(command)

        if (result.failureClassification == DispatchFailureClassification.RETRYABLE) {
            throw RetryableDispatchMessageException(result)
        }

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
