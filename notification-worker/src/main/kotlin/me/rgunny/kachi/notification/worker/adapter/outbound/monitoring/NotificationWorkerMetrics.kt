package me.rgunny.kachi.notification.worker.adapter.outbound.monitoring

import io.micrometer.core.instrument.MeterRegistry
import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.DispatchFailureClassification
import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.DispatchNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.DispatchNotificationResult
import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.RecoverStaleProcessingDispatchResult
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.AvailableRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.ResolvedRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.UnavailableRecipient
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationResult
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.worker.adapter.outbound.recipient.RecipientResolveSource
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * notification-worker runtime metric recorder.
 *
 * worker adapter가 core dispatch/DLT 결과를 metric으로 변환한다.
 */
@Component
class NotificationWorkerMetrics(
    private val registry: MeterRegistry,
) {

    /**
     * core dispatch 결과를 고정된 metric 이름과
     * 제한된 tag 집합(channel, status, classification, result)으로 변환해 기록한다.
     */
    fun recordDispatch(
        command: DispatchNotificationCommand,
        result: DispatchNotificationResult,
        elapsed: Duration,
    ) {
        val metricResult = dispatchResult(result)
        registry.counter(
            NotificationWorkerMetricContract.Names.DISPATCH,
            NotificationWorkerMetricContract.Tags.CHANNEL, command.channel.name,
            NotificationWorkerMetricContract.Tags.STATUS, result.status.name,
            NotificationWorkerMetricContract.Tags.CLASSIFICATION, result.failureClassification.name,
            NotificationWorkerMetricContract.Tags.RESULT, metricResult,
        ).increment()
        registry.timer(
            NotificationWorkerMetricContract.Names.DISPATCH_DURATION,
            NotificationWorkerMetricContract.Tags.CHANNEL, command.channel.name,
            NotificationWorkerMetricContract.Tags.RESULT, metricResult,
        ).record(elapsed)
    }

    fun recordInvalidDispatchPayload(elapsed: Duration) {
        recordDispatchFailure(null, NotificationWorkerMetricContract.Results.Dispatch.INVALID_PAYLOAD, elapsed)
    }

    fun recordDispatchNotReady(command: DispatchNotificationCommand, elapsed: Duration) {
        recordDispatchFailure(command, NotificationWorkerMetricContract.Results.Dispatch.NOT_READY, elapsed)
    }

    fun recordDispatchFailure(
        command: DispatchNotificationCommand,
        elapsed: Duration,
    ) {
        recordDispatchFailure(command, NotificationWorkerMetricContract.Results.Dispatch.FAILED, elapsed)
    }

    private fun recordDispatchFailure(
        command: DispatchNotificationCommand?,
        result: String,
        elapsed: Duration,
    ) {
        // core 결과가 없으므로 상태와 분류를 추정하지 않는다.
        // 확인할 수 없는 tag는 고정값으로 채워 시계열 schema를 유지한다.
        registry.counter(
            NotificationWorkerMetricContract.Names.DISPATCH,
            NotificationWorkerMetricContract.Tags.CHANNEL,
            command?.channel?.name ?: NotificationWorkerMetricContract.TagValues.UNKNOWN,
            NotificationWorkerMetricContract.Tags.STATUS, NotificationWorkerMetricContract.TagValues.UNKNOWN,
            NotificationWorkerMetricContract.Tags.CLASSIFICATION, DispatchFailureClassification.NONE.name,
            NotificationWorkerMetricContract.Tags.RESULT, result,
        ).increment()
        registry.timer(
            NotificationWorkerMetricContract.Names.DISPATCH_DURATION,
            NotificationWorkerMetricContract.Tags.CHANNEL,
            command?.channel?.name ?: NotificationWorkerMetricContract.TagValues.UNKNOWN,
            NotificationWorkerMetricContract.Tags.RESULT, result,
        ).record(elapsed)
    }

    fun recordDltPersisted() {
        registry.counter(
            NotificationWorkerMetricContract.Names.DLT_PERSIST,
            NotificationWorkerMetricContract.Tags.RESULT,
            NotificationWorkerMetricContract.Results.Dlt.PERSISTED,
        ).increment()
    }

    fun recordDltPersistFailure() {
        registry.counter(
            NotificationWorkerMetricContract.Names.DLT_PERSIST,
            NotificationWorkerMetricContract.Tags.RESULT,
            NotificationWorkerMetricContract.Results.Dlt.PERSIST_FAILED,
        ).increment()
    }

    fun recordSender(
        channel: NotificationChannel,
        result: SendNotificationResult,
        elapsed: Duration,
    ) {
        val metricResult = senderResult(result)
        // 상세 외부 에러 코드는 tag로 쓰지 않는다.
        // cardinality가 제한된 core FailureCategory만 운영 분류로 사용한다.
        val failureCategory = when (result) {
            is SendNotificationResult.Success -> NotificationWorkerMetricContract.TagValues.NONE
            is SendNotificationResult.RateLimited -> result.failure.category.name
            is SendNotificationResult.TransientFailure -> result.failure.category.name
            is SendNotificationResult.PermanentFailure -> result.failure.category.name
        }

        registry.counter(
            NotificationWorkerMetricContract.Names.SENDER,
            NotificationWorkerMetricContract.Tags.CHANNEL, channel.name,
            NotificationWorkerMetricContract.Tags.RESULT, metricResult,
            NotificationWorkerMetricContract.Tags.FAILURE_CATEGORY, failureCategory,
        ).increment()
        registry.timer(
            NotificationWorkerMetricContract.Names.SENDER_DURATION,
            NotificationWorkerMetricContract.Tags.CHANNEL, channel.name,
            NotificationWorkerMetricContract.Tags.RESULT, metricResult,
        ).record(elapsed)
    }

    fun recordSenderUnexpected(
        channel: NotificationChannel,
        elapsed: Duration,
    ) {
        registry.counter(
            NotificationWorkerMetricContract.Names.SENDER,
            NotificationWorkerMetricContract.Tags.CHANNEL, channel.name,
            NotificationWorkerMetricContract.Tags.RESULT,
            NotificationWorkerMetricContract.Results.Sender.UNEXPECTED,
            NotificationWorkerMetricContract.Tags.FAILURE_CATEGORY,
            NotificationWorkerMetricContract.TagValues.UNKNOWN,
        ).increment()
        registry.timer(
            NotificationWorkerMetricContract.Names.SENDER_DURATION,
            NotificationWorkerMetricContract.Tags.CHANNEL, channel.name,
            NotificationWorkerMetricContract.Tags.RESULT,
            NotificationWorkerMetricContract.Results.Sender.UNEXPECTED,
        ).record(elapsed)
    }

    fun recordRecipientResolved(
        channel: NotificationChannel,
        resolved: ResolvedRecipient,
        source: RecipientResolveSource,
    ) {
        // 수신자 id·주소·사유는 시계열을 늘리거나 주소를 노출하므로 tag로 쓰지 않는다.
        val metricResult = when (resolved) {
            is AvailableRecipient -> NotificationWorkerMetricContract.Results.RecipientResolve.AVAILABLE
            is UnavailableRecipient -> NotificationWorkerMetricContract.Results.RecipientResolve.UNAVAILABLE
        }
        recordRecipientResolve(channel, metricResult, source)
    }

    fun recordRecipientResolveFailed(
        channel: NotificationChannel,
        source: RecipientResolveSource,
    ) {
        recordRecipientResolve(channel, NotificationWorkerMetricContract.Results.RecipientResolve.FAILED, source)
    }

    private fun recordRecipientResolve(
        channel: NotificationChannel,
        result: String,
        source: RecipientResolveSource,
    ) {
        registry.counter(
            NotificationWorkerMetricContract.Names.RECIPIENT_RESOLVE,
            NotificationWorkerMetricContract.Tags.CHANNEL, channel.name,
            NotificationWorkerMetricContract.Tags.RESULT, result,
            NotificationWorkerMetricContract.Tags.SOURCE, recipientResolveSource(source),
        ).increment()
    }

    fun recordProcessingRecovery(result: RecoverStaleProcessingDispatchResult, elapsed: Duration) {
        // Timer count로 tick 횟수를 표현하고 Counter는 실제 회수 결과의 단건 수량만 누적한다.
        registry.timer(
            NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY_DURATION,
            NotificationWorkerMetricContract.Tags.RESULT,
            NotificationWorkerMetricContract.Results.ProcessingRecoveryTick.COMPLETED,
        ).record(elapsed)

        if (result.recoveredToRetryWait > 0) {
            registry.counter(
                NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY,
                NotificationWorkerMetricContract.Tags.RESULT,
                NotificationWorkerMetricContract.Results.ProcessingRecovery.RETRY_WAIT,
            )
                .increment(result.recoveredToRetryWait.toDouble())
        }
        if (result.recoveredToDead > 0) {
            registry.counter(
                NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY,
                NotificationWorkerMetricContract.Tags.RESULT,
                NotificationWorkerMetricContract.Results.ProcessingRecovery.DEAD,
            )
                .increment(result.recoveredToDead.toDouble())
        }
        if (result.staleProcessingSkipped > 0) {
            registry.counter(
                NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY,
                NotificationWorkerMetricContract.Tags.RESULT,
                NotificationWorkerMetricContract.Results.ProcessingRecovery.SKIPPED,
            )
                .increment(result.staleProcessingSkipped.toDouble())
        }
    }

    fun recordProcessingRecoveryFailure(elapsed: Duration) {
        registry.timer(
            NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY_DURATION,
            NotificationWorkerMetricContract.Tags.RESULT,
            NotificationWorkerMetricContract.Results.ProcessingRecoveryTick.FAILED,
        ).record(elapsed)
    }

    private fun dispatchResult(result: DispatchNotificationResult): String {
        // 중복 consume은 저장된 최종 status와 무관하게
        // 이번 호출에서 dispatch하지 않은 결과가 우선한다.
        if (result.duplicated) {
            return NotificationWorkerMetricContract.Results.Dispatch.DUPLICATED
        }
        return when (result.status) {
            NotificationStatus.SENT -> NotificationWorkerMetricContract.Results.Dispatch.SENT
            NotificationStatus.RETRY_WAIT -> NotificationWorkerMetricContract.Results.Dispatch.RETRY_WAIT
            NotificationStatus.DEAD -> NotificationWorkerMetricContract.Results.Dispatch.DEAD
            NotificationStatus.SUPPRESSED -> NotificationWorkerMetricContract.Results.Dispatch.SUPPRESSED
            // 현재 core 불변식 밖의 상태를 숨기지 않고 운영 이상 신호로 남긴다.
            else -> NotificationWorkerMetricContract.Results.Dispatch.UNEXPECTED_STATUS
        }
    }

    private fun recipientResolveSource(source: RecipientResolveSource): String {
        return when (source) {
            RecipientResolveSource.CACHE -> NotificationWorkerMetricContract.Sources.RecipientResolve.CACHE
            RecipientResolveSource.USER_SERVICE -> {
                NotificationWorkerMetricContract.Sources.RecipientResolve.USER_SERVICE
            }
        }
    }

    private fun senderResult(result: SendNotificationResult): String {
        return when (result) {
            is SendNotificationResult.Success -> NotificationWorkerMetricContract.Results.Sender.SUCCESS
            is SendNotificationResult.RateLimited -> NotificationWorkerMetricContract.Results.Sender.RATE_LIMITED
            is SendNotificationResult.TransientFailure -> {
                NotificationWorkerMetricContract.Results.Sender.TRANSIENT_FAILURE
            }
            is SendNotificationResult.PermanentFailure -> {
                NotificationWorkerMetricContract.Results.Sender.PERMANENT_FAILURE
            }
        }
    }
}
