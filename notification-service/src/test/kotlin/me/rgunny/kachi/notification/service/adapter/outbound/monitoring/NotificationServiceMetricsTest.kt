package me.rgunny.kachi.notification.service.adapter.outbound.monitoring

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import me.rgunny.kachi.notification.application.port.dto.PublishNotificationDispatchResult
import me.rgunny.kachi.notification.application.port.dto.RequestNotificationResult
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.service.adapter.outbound.monitoring.NotificationServiceMetricContract.RequestSource
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("NotificationServiceMetrics")
class NotificationServiceMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = NotificationServiceMetrics(registry)

    @Test
    @DisplayName("request 결과를 고정된 이름과 태그로 기록한다")
    fun recordRequestResults() {
        metrics.recordRequest(RequestSource.HTTP, NotificationChannel.SLACK, requestResult(false), ELAPSED)
        metrics.recordRequest(RequestSource.KAFKA, NotificationChannel.EMAIL, requestResult(true), ELAPSED)
        metrics.recordInvalidRequest(RequestSource.KAFKA, null, ELAPSED)
        metrics.recordRequestFailure(RequestSource.HTTP, NotificationChannel.SMS, ELAPSED)

        assertCounter(
            NotificationServiceMetricContract.Names.REQUEST,
            1.0,
            "source", "http",
            "channel", "SLACK",
            "result", "accepted",
        )
        assertCounter(
            NotificationServiceMetricContract.Names.REQUEST,
            1.0,
            "source", "kafka",
            "channel", "EMAIL",
            "result", "duplicated",
        )
        assertCounter(
            NotificationServiceMetricContract.Names.REQUEST,
            1.0,
            "source", "kafka",
            "channel", "UNKNOWN",
            "result", "invalid",
        )
        assertCounter(
            NotificationServiceMetricContract.Names.REQUEST,
            1.0,
            "source", "http",
            "channel", "SMS",
            "result", "failed",
        )
        assertEquals(
            1L,
            registry.get(NotificationServiceMetricContract.Names.REQUEST_DURATION)
                .tags("source", "http", "channel", "SLACK", "result", "accepted")
                .timer()
                .count(),
        )
        assertTagKeys(
            NotificationServiceMetricContract.Names.REQUEST,
            setOf("source", "channel", "result"),
        )
        assertTagKeys(
            NotificationServiceMetricContract.Names.REQUEST_DURATION,
            setOf("source", "channel", "result"),
        )
    }

    @Test
    @DisplayName("outbox tick과 단건 처리 수를 서로 다른 결과 계약으로 기록한다")
    fun recordOutboxPublishResults() {
        metrics.recordOutboxPublishTick(
            PublishNotificationDispatchResult(
                processed = 9,
                published = 6,
                failed = 3,
                publishTickCompletedAt = NOW,
            ),
            ELAPSED,
        )
        metrics.recordOutboxPublishTickFailure(ELAPSED)

        assertCounter(
            NotificationServiceMetricContract.Names.OUTBOX_PUBLISH,
            6.0,
            "result", "published",
        )
        assertCounter(
            NotificationServiceMetricContract.Names.OUTBOX_PUBLISH,
            3.0,
            "result", "failed",
        )
        assertEquals(
            1L,
            registry.get(NotificationServiceMetricContract.Names.OUTBOX_PUBLISH_DURATION)
                .tag("result", "completed")
                .timer()
                .count(),
        )
        assertEquals(
            1L,
            registry.get(NotificationServiceMetricContract.Names.OUTBOX_PUBLISH_DURATION)
                .tag("result", "tick_failed")
                .timer()
                .count(),
        )
    }

    @Test
    @DisplayName("Micrometer 이름을 기존 Prometheus metric 이름으로 노출한다")
    fun exposePrometheusNames() {
        val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val prometheusMetrics = NotificationServiceMetrics(prometheusRegistry)
        prometheusMetrics.recordRequest(RequestSource.HTTP, NotificationChannel.SLACK, requestResult(false), ELAPSED)
        prometheusMetrics.recordOutboxPublishTick(
            PublishNotificationDispatchResult(1, 1, 0, NOW),
            ELAPSED,
        )

        val scrape = prometheusRegistry.scrape()
        assertTrue(scrape.contains("kachi_notification_request_total"))
        assertTrue(scrape.contains("kachi_notification_request_duration_seconds_count"))
        assertTrue(scrape.contains("kachi_notification_outbox_publish_total"))
        assertTrue(scrape.contains("kachi_notification_outbox_publish_duration_seconds_count"))
    }

    private fun requestResult(duplicated: Boolean): RequestNotificationResult {
        return RequestNotificationResult(
            notificationId = NotificationId.newId(),
            status = NotificationStatus.REQUESTED,
            duplicated = duplicated,
            acceptedAt = NOW,
        )
    }

    private fun assertCounter(name: String, expected: Double, vararg tags: String) {
        assertEquals(expected, registry.get(name).tags(*tags).counter().count())
    }

    private fun assertTagKeys(name: String, expected: Set<String>) {
        registry.find(name).meters().forEach { meter ->
            assertEquals(expected, meter.id.tags.map { it.key }.toSet())
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-20T00:00:00Z")
        val ELAPSED: Duration = Duration.ofMillis(25)
    }
}
