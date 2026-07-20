package me.rgunny.kachi.notification.worker.adapter.outbound.monitoring

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import me.rgunny.kachi.notification.application.port.dto.DispatchFailureClassification
import me.rgunny.kachi.notification.application.port.dto.DispatchNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.DispatchNotificationResult
import me.rgunny.kachi.notification.application.port.dto.RecoverStaleProcessingDispatchResult
import me.rgunny.kachi.notification.application.port.dto.SendNotificationResult
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryFailureCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("NotificationWorkerMetrics")
class NotificationWorkerMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = NotificationWorkerMetrics(registry)

    @Test
    @DisplayName("dispatch 결과와 adapter 실패를 고정된 태그 집합으로 기록한다")
    fun recordDispatchResults() {
        metrics.recordDispatch(command(), dispatchResult(NotificationStatus.SENT), ELAPSED)
        metrics.recordDispatch(command(), dispatchResult(NotificationStatus.RETRY_WAIT), ELAPSED)
        metrics.recordDispatch(command(), dispatchResult(NotificationStatus.DEAD), ELAPSED)
        metrics.recordDispatch(command(), dispatchResult(NotificationStatus.SENT, duplicated = true), ELAPSED)
        metrics.recordDispatch(command(), dispatchResult(NotificationStatus.REQUESTED), ELAPSED)
        metrics.recordInvalidDispatchPayload(ELAPSED)
        metrics.recordDispatchNotReady(command(), ELAPSED)
        metrics.recordDispatchFailure(command(), ELAPSED)

        listOf("sent", "retry_wait", "dead", "duplicated").forEach { result ->
            assertCounter(
                NotificationWorkerMetricContract.Names.DISPATCH,
                1.0,
                "channel", "SLACK",
                "status", if (result == "duplicated") "SENT" else result.uppercase(),
                "classification", "NONE",
                "result", result,
            )
        }
        assertCounter(
            NotificationWorkerMetricContract.Names.DISPATCH,
            1.0,
            "channel", "UNKNOWN",
            "status", "UNKNOWN",
            "classification", "NONE",
            "result", "invalid_payload",
        )
        listOf("not_ready", "failed").forEach { result ->
            assertCounter(
                NotificationWorkerMetricContract.Names.DISPATCH,
                1.0,
                "channel", "SLACK",
                "status", "UNKNOWN",
                "classification", "NONE",
                "result", result,
            )
        }
        assertCounter(
            NotificationWorkerMetricContract.Names.DISPATCH,
            1.0,
            "channel", "SLACK",
            "status", "REQUESTED",
            "classification", "NONE",
            "result", "unexpected_status",
        )
        assertTagKeys(
            NotificationWorkerMetricContract.Names.DISPATCH,
            setOf("channel", "status", "classification", "result"),
        )
        assertTagKeys(
            NotificationWorkerMetricContract.Names.DISPATCH_DURATION,
            setOf("channel", "result"),
        )
    }

    @Test
    @DisplayName("sender 결과와 failure category를 channel 기준으로 기록한다")
    fun recordSenderResults() {
        metrics.recordSender(NotificationChannel.SLACK, SendNotificationResult.Success(), ELAPSED)
        metrics.recordSender(
            NotificationChannel.SLACK,
            SendNotificationResult.RateLimited(RetryFailure.of(RetryFailureCode.VENDOR_RATE_LIMITED)),
            ELAPSED,
        )
        metrics.recordSender(
            NotificationChannel.SLACK,
            SendNotificationResult.TransientFailure(RetryFailure.of(RetryFailureCode.VENDOR_TIMEOUT)),
            ELAPSED,
        )
        metrics.recordSender(
            NotificationChannel.SLACK,
            SendNotificationResult.PermanentFailure(RetryFailure.of(RetryFailureCode.INVALID_RECIPIENT)),
            ELAPSED,
        )
        metrics.recordSenderUnexpected(NotificationChannel.SLACK, ELAPSED)

        mapOf(
            "success" to "NONE",
            "rate_limited" to "RATE_LIMITED",
            "transient_failure" to "TIMEOUT",
            "permanent_failure" to "VALIDATION_ERROR",
            "unexpected" to "UNKNOWN",
        ).forEach { (result, category) ->
            assertCounter(
                NotificationWorkerMetricContract.Names.SENDER,
                1.0,
                "channel", "SLACK",
                "result", result,
                "failure_category", category,
            )
        }
        assertTagKeys(
            NotificationWorkerMetricContract.Names.SENDER,
            setOf("channel", "result", "failure_category"),
        )
        assertTagKeys(
            NotificationWorkerMetricContract.Names.SENDER_DURATION,
            setOf("channel", "result"),
        )
    }

    @Test
    @DisplayName("DLT 영속 성공과 실패를 기록한다")
    fun recordDltResults() {
        metrics.recordDltPersisted()
        metrics.recordDltPersistFailure()

        val name = NotificationWorkerMetricContract.Names.DLT_PERSIST
        assertCounter(name, 1.0, "result", "persisted")
        assertCounter(name, 1.0, "result", "persist_failed")
        assertTagKeys(name, setOf("result"))
    }

    @Test
    @DisplayName("processing recovery tick과 상태별 회수 수량을 기록한다")
    fun recordProcessingRecoveryResults() {
        metrics.recordProcessingRecovery(
            RecoverStaleProcessingDispatchResult(
                staleProcessingFound = 7,
                staleProcessingRecovered = 5,
                recoveredToRetryWait = 3,
                recoveredToDead = 2,
                staleProcessingSkipped = 2,
                recoveryTickCompletedAt = NOW,
            ),
            ELAPSED,
        )
        metrics.recordProcessingRecoveryFailure(ELAPSED)

        assertCounter(
            NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY,
            3.0,
            "result", "retry_wait",
        )
        assertCounter(
            NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY,
            2.0,
            "result", "dead",
        )
        assertCounter(
            NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY,
            2.0,
            "result", "skipped",
        )
        assertTagKeys(NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY, setOf("result"))
        assertTagKeys(NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY_DURATION, setOf("result"))
    }

    @Test
    @DisplayName("Micrometer 이름을 기존 Prometheus metric 이름으로 노출한다")
    fun exposePrometheusNames() {
        val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val prometheusMetrics = NotificationWorkerMetrics(prometheusRegistry)
        prometheusMetrics.recordDispatch(command(), dispatchResult(NotificationStatus.SENT), ELAPSED)
        prometheusMetrics.recordSender(NotificationChannel.SLACK, SendNotificationResult.Success(), ELAPSED)
        prometheusMetrics.recordDltPersistFailure()
        prometheusMetrics.recordProcessingRecovery(
            RecoverStaleProcessingDispatchResult(1, 1, 1, 0, 0, NOW),
            ELAPSED,
        )

        val scrape = prometheusRegistry.scrape()
        listOf(
            "kachi_notification_dispatch_total",
            "kachi_notification_dispatch_duration_seconds_count",
            "kachi_notification_dlt_persist_total",
            "kachi_notification_sender_total",
            "kachi_notification_sender_duration_seconds_count",
            "kachi_notification_processing_recovery_total",
            "kachi_notification_processing_recovery_duration_seconds_count",
        ).forEach { prometheusName ->
            assertTrue(scrape.contains(prometheusName), "missing Prometheus metric: $prometheusName")
        }
    }

    private fun command(): DispatchNotificationCommand {
        return DispatchNotificationCommand(
            notificationId = NotificationId.newId(),
            requestId = "request-1",
            channel = NotificationChannel.SLACK,
            recipient = "C123",
            message = "hello",
        )
    }

    private fun dispatchResult(
        status: NotificationStatus,
        duplicated: Boolean = false,
    ): DispatchNotificationResult {
        return DispatchNotificationResult(
            notificationId = NotificationId.newId(),
            status = status,
            duplicated = duplicated,
            dispatchAttempted = !duplicated,
            dispatchCompletedAt = NOW,
            failureClassification = DispatchFailureClassification.NONE,
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
