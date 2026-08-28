package me.rgunny.kachi.notification.routing.adapter.inbound.messaging

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.RouteQuarantineNotificationUseCase
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteQuarantineCommand
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteNotificationOutcome
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteNotificationResult
import me.rgunny.kachi.notification.routing.domain.RoutingJobId
import me.rgunny.kachi.notification.routing.adapter.outbound.monitoring.NotificationRoutingMetricContract
import me.rgunny.kachi.notification.routing.adapter.outbound.monitoring.NotificationRoutingMetrics
import me.rgunny.kachi.notification.routing.support.RoutingTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("AiKeywordQuarantinedKafkaListener")
class AiKeywordQuarantinedKafkaListenerTest {

    private val useCase = CapturingRouteQuarantineUseCase()
    private val registry = SimpleMeterRegistry()
    private val listener = AiKeywordQuarantinedKafkaListener(
        routeQuarantineNotificationUseCase = useCase,
        jsonMapper = JsonMapper.builder().findAndAddModules().build(),
        metrics = NotificationRoutingMetrics(registry),
    )

    @Test
    @DisplayName("정상 payload는 격리 routing use case로 전달하고 계측한다")
    fun consume() {
        listener.consume(RoutingTestFixture.KEYWORD_QUARANTINED_JSON.trimIndent())

        val command = useCase.lastCommand
        requireNotNull(command)
        assertEquals("quarantine-1:${RoutingTestFixture.NOW.toEpochMilli()}", command.eventKey)
        assertEquals("tesla", command.keyword)
        assertCounter("QUARANTINE", "skipped")
    }

    @Test
    @DisplayName("JSON 파싱 실패는 invalid ai event 예외로 분류한다")
    fun rejectInvalidJson() {
        assertFailsWith<InvalidAiEventMessageException> { listener.consume("not json") }

        assertNull(useCase.lastCommand)
        assertCounter("QUARANTINE", "invalid")
    }

    @Test
    @DisplayName("use case 예외는 전파하고 failed를 계측한다")
    fun propagateFailure() {
        useCase.failure = IllegalStateException("mongo down")

        assertFailsWith<IllegalStateException> { listener.consume(RoutingTestFixture.KEYWORD_QUARANTINED_JSON) }

        assertCounter("QUARANTINE", "failed")
    }

    private fun assertCounter(kind: String, result: String) {
        assertEquals(
            1.0,
            registry.get(NotificationRoutingMetricContract.Names.ROUTING)
                .tags("kind", kind, "result", result)
                .counter()
                .count(),
        )
    }

    private class CapturingRouteQuarantineUseCase : RouteQuarantineNotificationUseCase {
        var lastCommand: RouteQuarantineCommand? = null
            private set
        var failure: RuntimeException? = null

        override suspend fun routeQuarantine(command: RouteQuarantineCommand): RouteNotificationResult {
            lastCommand = command
            failure?.let { throw it }
            return RouteNotificationResult(RoutingJobId.newId(), RouteNotificationOutcome.SKIPPED, 3, 3)
        }
    }
}
