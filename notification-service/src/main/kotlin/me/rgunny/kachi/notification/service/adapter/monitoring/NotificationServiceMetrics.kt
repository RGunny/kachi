package me.rgunny.kachi.notification.service.adapter.monitoring

import io.micrometer.core.instrument.MeterRegistry
import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.PublishNotificationDispatchResult
import me.rgunny.kachi.notification.application.port.inbound.request.model.RequestNotificationResult
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.service.adapter.monitoring.NotificationServiceMetricContract.RequestSource
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * notification-service runtime metric recorder.
 *
 * notification-core는 Spring/Micrometer를 모르게 두고,
 * runtime adapter가 use case 결과를 metric으로 변환한다.
 */
@Component
class NotificationServiceMetrics(
    private val registry: MeterRegistry,
) {

    /** core 접수 결과의 duplicated 여부를 운영 metric 결과로 변환한다. */
    fun recordRequest(
        source: RequestSource,
        channel: NotificationChannel,
        result: RequestNotificationResult,
        elapsed: Duration,
    ) {
        val metricResult = if (result.duplicated) {
            NotificationServiceMetricContract.Results.Request.DUPLICATED
        } else {
            NotificationServiceMetricContract.Results.Request.ACCEPTED
        }
        registry.counter(
            NotificationServiceMetricContract.Names.REQUEST,
            NotificationServiceMetricContract.Tags.SOURCE, source.value,
            NotificationServiceMetricContract.Tags.CHANNEL, channel.name,
            NotificationServiceMetricContract.Tags.RESULT, metricResult,
        ).increment()
        registry.timer(
            NotificationServiceMetricContract.Names.REQUEST_DURATION,
            NotificationServiceMetricContract.Tags.SOURCE, source.value,
            NotificationServiceMetricContract.Tags.CHANNEL, channel.name,
            NotificationServiceMetricContract.Tags.RESULT, metricResult,
        ).record(elapsed)
    }

    fun recordInvalidRequest(
        source: RequestSource,
        channel: NotificationChannel?,
        elapsed: Duration,
    ) {
        recordRequestFailure(source, channel, NotificationServiceMetricContract.Results.Request.INVALID, elapsed)
    }

    fun recordRequestFailure(
        source: RequestSource,
        channel: NotificationChannel?,
        elapsed: Duration,
    ) {
        recordRequestFailure(source, channel, NotificationServiceMetricContract.Results.Request.FAILED, elapsed)
    }

    private fun recordRequestFailure(
        source: RequestSource,
        channel: NotificationChannel?,
        result: String,
        elapsed: Duration,
    ) {
        registry.counter(
            NotificationServiceMetricContract.Names.REQUEST,
            NotificationServiceMetricContract.Tags.SOURCE, source.value,
            NotificationServiceMetricContract.Tags.CHANNEL,
            channel?.name ?: NotificationServiceMetricContract.TagValues.UNKNOWN,
            NotificationServiceMetricContract.Tags.RESULT, result,
        ).increment()
        registry.timer(
            NotificationServiceMetricContract.Names.REQUEST_DURATION,
            NotificationServiceMetricContract.Tags.SOURCE, source.value,
            NotificationServiceMetricContract.Tags.CHANNEL,
            channel?.name ?: NotificationServiceMetricContract.TagValues.UNKNOWN,
            NotificationServiceMetricContract.Tags.RESULT, result,
        ).record(elapsed)
    }

    fun recordOutboxPublishTick(result: PublishNotificationDispatchResult, elapsed: Duration) {
        // Timer count가 scheduler 실행 횟수도 나타내므로 별도 tick counter를 만들지 않는다.
        registry.timer(
            NotificationServiceMetricContract.Names.OUTBOX_PUBLISH_DURATION,
            NotificationServiceMetricContract.Tags.RESULT,
            NotificationServiceMetricContract.Results.OutboxPublishTick.COMPLETED,
        ).record(elapsed)

        // tick 결과의 합계가 아니라 실제 단건 결과만 누적한다.
        // 이렇게 해야 성공률 계산의 분모가 실제 발행 시도 건수와 일치한다.
        if (result.published > 0) {
            registry.counter(
                NotificationServiceMetricContract.Names.OUTBOX_PUBLISH,
                NotificationServiceMetricContract.Tags.RESULT,
                NotificationServiceMetricContract.Results.OutboxPublish.PUBLISHED,
            )
                .increment(result.published.toDouble())
        }
        if (result.failed > 0) {
            registry.counter(
                NotificationServiceMetricContract.Names.OUTBOX_PUBLISH,
                NotificationServiceMetricContract.Tags.RESULT,
                NotificationServiceMetricContract.Results.OutboxPublish.FAILED,
            )
                .increment(result.failed.toDouble())
        }
    }

    /** tick 자체가 실패해 단건 결과를 만들 수 없는 경우 처리 시간만 실패로 기록한다. */
    fun recordOutboxPublishTickFailure(elapsed: Duration) {
        registry.timer(
            NotificationServiceMetricContract.Names.OUTBOX_PUBLISH_DURATION,
            NotificationServiceMetricContract.Tags.RESULT,
            NotificationServiceMetricContract.Results.OutboxPublishTick.FAILED,
        ).record(elapsed)
    }
}
