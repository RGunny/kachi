package me.rgunny.kachi.notification.service.adapter.inbound.messaging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.inbound.RequestNotificationUseCase
import me.rgunny.kachi.notification.contract.NotificationRequestedEvent
import me.rgunny.kachi.notification.service.adapter.outbound.monitoring.NotificationServiceMetrics
import me.rgunny.kachi.notification.service.adapter.outbound.monitoring.NotificationServiceMetricContract.RequestSource
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/**
 * notification.requested Kafka 인입 adapter.
 *
 * listener는 메시지 계약을 core command로 변환하고,
 * 접수/멱등/outbox 생성 규칙은 core use case에 위임한다.
 */
@Component
class NotificationRequestedKafkaListener(
    private val requestNotificationUseCase: RequestNotificationUseCase,
    private val jsonMapper: JsonMapper,
    private val metrics: NotificationServiceMetrics,
) {

    @KafkaListener(
        topics = ["\${kachi.notification.request.topic}"],
        groupId = "\${kachi.notification.request.group-id}",
        containerFactory = "notificationRequestedKafkaListenerContainerFactory",
    )
    fun consume(@Payload payload: String) = runBlocking {
        val startedAt = System.nanoTime()
        // 1. 역직렬화 단계에서는 channel을 신뢰할 수 없으므로
        // invalid 결과를 UNKNOWN channel로 기록한다.
        val event = try {
            readEvent(payload)
        } catch (exception: InvalidNotificationRequestedMessageException) {
            metrics.recordInvalidRequest(
                source = RequestSource.KAFKA,
                channel = null,
                elapsed = elapsed(startedAt),
            )
            throw exception
        }
        // 2. schema와 필드 검증을 통과한 event만 core command로 변환한다.
        val command = try {
            NotificationRequestedEventMapper.toCommand(event)
        } catch (exception: IllegalArgumentException) {
            metrics.recordInvalidRequest(
                source = RequestSource.KAFKA,
                channel = null,
                elapsed = elapsed(startedAt),
            )
            throw InvalidNotificationRequestedMessageException(
                message = "invalid notification requested event. requestId=${event.requestId}",
                cause = exception,
            )
        }

        // 3. 접수/멱등/outbox 생성은 core 결과를 기준으로 accepted 또는 duplicated로 계측한다.
        val result = try {
            requestNotificationUseCase.request(command)
        } catch (exception: CancellationException) {
            // coroutine 취소는 업무 실패가 아니므로 metric으로 변환하지 않는다.
            throw exception
        } catch (exception: Exception) {
            metrics.recordRequestFailure(
                source = RequestSource.KAFKA,
                channel = command.channel,
                elapsed = elapsed(startedAt),
            )
            throw exception
        }
        metrics.recordRequest(
            source = RequestSource.KAFKA,
            channel = command.channel,
            result = result,
            elapsed = elapsed(startedAt),
        )

        log.info(
            "notification requested event consumed requestId={} notificationId={} status={} duplicated={}",
            event.requestId,
            result.notificationId.id,
            result.status,
            result.duplicated,
        )
    }

    private fun readEvent(payload: String): NotificationRequestedEvent {
        return try {
            jsonMapper.readValue(payload, NotificationRequestedEvent::class.java)
        } catch (exception: Exception) {
            throw InvalidNotificationRequestedMessageException(
                message = "invalid notification requested payload",
                cause = exception,
            )
        }
    }

    private fun elapsed(startedAt: Long): Duration {
        return Duration.ofNanos(System.nanoTime() - startedAt)
    }

    private companion object {
        val log = LoggerFactory.getLogger(NotificationRequestedKafkaListener::class.java)
    }
}
