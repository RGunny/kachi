package me.rgunny.kachi.notification.service.adapter.inbound.messaging

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.inbound.RequestNotificationUseCase
import me.rgunny.kachi.notification.contract.NotificationRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * notification.requested Kafka 인입 adapter.
 *
 * listener는 메시지 계약을 core command로 변환하고, 접수/멱등/outbox 생성 규칙은 core use case에 위임한다.
 */
@Component
class NotificationRequestedKafkaListener(
    private val requestNotificationUseCase: RequestNotificationUseCase,
    private val jsonMapper: JsonMapper,
) {

    @KafkaListener(
        topics = ["\${kachi.notification.request.topic}"],
        groupId = "\${kachi.notification.request.group-id}",
    )
    fun consume(@Payload payload: String) = runBlocking {
        val event = jsonMapper.readValue(payload, NotificationRequestedEvent::class.java)
        val result = requestNotificationUseCase.request(NotificationRequestedEventMapper.toCommand(event))

        log.info(
            "notification requested event consumed requestId={} notificationId={} status={} duplicated={}",
            event.requestId,
            result.notificationId.id,
            result.status,
            result.duplicated,
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(NotificationRequestedKafkaListener::class.java)
    }
}
