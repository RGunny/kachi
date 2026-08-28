package me.rgunny.kachi.notification.routing.adapter.inbound.messaging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.contract.AiKeywordQuarantinedEvent
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.RouteQuarantineNotificationUseCase
import me.rgunny.kachi.notification.routing.domain.RoutingJobKind
import me.rgunny.kachi.notification.routing.adapter.outbound.monitoring.NotificationRoutingMetrics
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/**
 * ai.keyword.quarantined Kafka 인입 adapter.
 * 관리자 x 채널로 라우팅한다.
 */
@Component
class AiKeywordQuarantinedKafkaListener(
    private val routeQuarantineNotificationUseCase: RouteQuarantineNotificationUseCase,
    private val jsonMapper: JsonMapper,
    private val metrics: NotificationRoutingMetrics,
) {

    @KafkaListener(
        topics = ["\${kachi.notification.routing.topics.keyword-quarantined}"],
        groupId = "\${kachi.notification.routing.group-id}",
        containerFactory = "notificationRoutingKafkaListenerContainerFactory",
        properties = ["auto.offset.reset=\${kachi.notification.routing.auto-offset-reset}"],
    )
    fun consume(@Payload payload: String) = runBlocking {
        val startedAt = System.nanoTime()
        val command = try {
            AiKeywordQuarantinedEventMapper.toCommand(readEvent(payload))
        } catch (exception: IllegalArgumentException) {
            metrics.recordInvalidRoutingMessage(RoutingJobKind.QUARANTINE, elapsed(startedAt))
            throw InvalidAiEventMessageException("invalid ai keyword quarantined event", exception)
        } catch (exception: InvalidAiEventMessageException) {
            metrics.recordInvalidRoutingMessage(RoutingJobKind.QUARANTINE, elapsed(startedAt))
            throw exception
        }

        val result = try {
            routeQuarantineNotificationUseCase.routeQuarantine(command)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            metrics.recordRoutingFailure(RoutingJobKind.QUARANTINE, elapsed(startedAt))
            throw exception
        }
        metrics.recordRouting(RoutingJobKind.QUARANTINE, result, elapsed(startedAt))

        log.info(
            "ai keyword quarantine routed eventKey={} outcome={} targets={} published={}",
            command.eventKey, result.outcome, result.targetCount, result.publishedCount,
        )
    }

    private fun readEvent(payload: String): AiKeywordQuarantinedEvent {
        return try {
            jsonMapper.readValue(payload, AiKeywordQuarantinedEvent::class.java)
        } catch (exception: Exception) {
            throw InvalidAiEventMessageException("invalid ai keyword quarantined payload", exception)
        }
    }

    private fun elapsed(startedAt: Long): Duration = Duration.ofNanos(System.nanoTime() - startedAt)

    private companion object {
        val log = LoggerFactory.getLogger(AiKeywordQuarantinedKafkaListener::class.java)
    }
}
