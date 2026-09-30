package me.rgunny.kachi.notification.routing.adapter.monitoring

import io.micrometer.core.instrument.MeterRegistry
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteNotificationOutcome
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteNotificationResult
import me.rgunny.kachi.notification.routing.domain.RoutingJobKind
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * notification-routing runtime metric recorder.
 *
 * application 계층은 Micrometer를 모르게 두고, 어댑터가 use case 결과를 metric으로 변환한다.
 */
@Component
class NotificationRoutingMetrics(
    private val registry: MeterRegistry,
) {

    fun recordRouting(kind: RoutingJobKind, result: RouteNotificationResult, elapsed: Duration) {
        val metricResult = when (result.outcome) {
            RouteNotificationOutcome.ROUTED -> NotificationRoutingMetricContract.Results.ROUTED
            RouteNotificationOutcome.SKIPPED -> NotificationRoutingMetricContract.Results.SKIPPED
        }
        record(kind, metricResult, elapsed)
    }

    fun recordInvalidRoutingMessage(kind: RoutingJobKind, elapsed: Duration) {
        record(kind, NotificationRoutingMetricContract.Results.INVALID, elapsed)
    }

    fun recordRoutingFailure(kind: RoutingJobKind, elapsed: Duration) {
        record(kind, NotificationRoutingMetricContract.Results.FAILED, elapsed)
    }

    private fun record(kind: RoutingJobKind, result: String, elapsed: Duration) {
        registry.counter(
            NotificationRoutingMetricContract.Names.ROUTING,
            NotificationRoutingMetricContract.Tags.KIND, kind.name,
            NotificationRoutingMetricContract.Tags.RESULT, result,
        ).increment()
        registry.timer(
            NotificationRoutingMetricContract.Names.ROUTING_DURATION,
            NotificationRoutingMetricContract.Tags.KIND, kind.name,
            NotificationRoutingMetricContract.Tags.RESULT, result,
        ).record(elapsed)
    }
}
