package me.rgunny.kachi.notification.routing.adapter.outbound.monitoring

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteNotificationOutcome
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteNotificationResult
import me.rgunny.kachi.notification.routing.domain.RoutingJobId
import me.rgunny.kachi.notification.routing.domain.RoutingJobKind
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

@DisplayName("NotificationRoutingMetrics")
class NotificationRoutingMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = NotificationRoutingMetrics(registry)

    @Test
    @DisplayName("routing 결과를 kind·result 태그로 기록한다")
    fun recordRoutingResults() {
        metrics.recordRouting(RoutingJobKind.SUMMARY, result(RouteNotificationOutcome.ROUTED), ELAPSED)
        metrics.recordRouting(RoutingJobKind.SUMMARY, result(RouteNotificationOutcome.SKIPPED), ELAPSED)
        metrics.recordInvalidRoutingMessage(RoutingJobKind.ADMIN, ELAPSED)
        metrics.recordRoutingFailure(RoutingJobKind.ADMIN, ELAPSED)

        assertCounter("SUMMARY", "routed")
        assertCounter("SUMMARY", "skipped")
        assertCounter("ADMIN", "invalid")
        assertCounter("ADMIN", "failed")
        assertEquals(
            1L,
            registry.get(NotificationRoutingMetricContract.Names.ROUTING_DURATION)
                .tags("kind", "SUMMARY", "result", "routed")
                .timer()
                .count(),
        )
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

    private fun result(outcome: RouteNotificationOutcome): RouteNotificationResult {
        return RouteNotificationResult(RoutingJobId.newId(), outcome, 2, 2)
    }

    private companion object {
        val ELAPSED: Duration = Duration.ofMillis(5)
    }
}
