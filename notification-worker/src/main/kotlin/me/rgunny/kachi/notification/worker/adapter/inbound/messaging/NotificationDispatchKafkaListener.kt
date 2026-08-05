package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.dto.DispatchFailureClassification
import me.rgunny.kachi.notification.application.port.dto.DispatchNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.DispatchNotificationUseCase
import me.rgunny.kachi.notification.contract.NotificationDispatchEvent
import me.rgunny.kachi.notification.worker.adapter.inbound.messaging.exception.InvalidDispatchMessageException
import me.rgunny.kachi.notification.worker.adapter.inbound.messaging.exception.RetryableDispatchMessageException
import me.rgunny.kachi.notification.worker.adapter.outbound.monitoring.NotificationWorkerMetrics
import me.rgunny.kachi.notification.exception.dispatch.DispatchNotReadyException
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/**
 * notification.dispatch Kafka 인입 adapter.
 */
@Component
class NotificationDispatchKafkaListener(
    private val dispatchUseCase: DispatchNotificationUseCase,
    private val jsonMapper: JsonMapper,
    private val metrics: NotificationWorkerMetrics,
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
        val startedAt = System.nanoTime()
        // 1. Kafka payload를 contract event로 역직렬화하고 core command로 변환한다.
        val command = try {
            readCommand(payload)
        } catch (exception: InvalidDispatchMessageException) {
            metrics.recordInvalidDispatchPayload(elapsed(startedAt))
            throw exception
        }

        // 2. 실제 상태 claim, vendor 호출, DB finalize는 core use case에 위임한다.
        val result = try {
            dispatchUseCase.dispatch(command)
        } catch (exception: DispatchNotReadyException) {
            metrics.recordDispatchNotReady(
                command = command,
                elapsed = elapsed(startedAt),
            )
            throw exception
        } catch (exception: CancellationException) {
            // coroutine 취소는 Kafka 재시도 대상인 업무 실패로 분류하지 않는다.
            throw exception
        } catch (exception: Exception) {
            metrics.recordDispatchFailure(
                command = command,
                elapsed = elapsed(startedAt),
            )
            throw exception
        }
        metrics.recordDispatch(command, result, elapsed(startedAt))

        // 3. 재시도 가능한 vendor 실패는 ack하지 않는다.
        // 예외를 넘겨 Kafka retry/DLT 정책을 태운다.
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

    private fun elapsed(startedAt: Long): Duration {
        return Duration.ofNanos(System.nanoTime() - startedAt)
    }
}
