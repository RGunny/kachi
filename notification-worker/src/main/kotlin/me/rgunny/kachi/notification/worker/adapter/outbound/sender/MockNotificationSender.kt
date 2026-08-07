package me.rgunny.kachi.notification.worker.adapter.outbound.sender

import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationResult
import me.rgunny.kachi.notification.application.port.outbound.sender.NotificationSender
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryFailureCode
import org.slf4j.LoggerFactory

/**
 * 실제 vendor adapter가 붙기 전 worker dispatch 흐름을 검증하기 위한 mock sender.
 *
 * 운영 발송 품질을 보장하는 구현이 아니라, Kafka listener -> core dispatch -> 상태 전이 경로를 닫기 위한 임시 adapter다.
 */
class MockNotificationSender(
    private val channels: Set<NotificationChannel>,
    private val mode: MockNotificationSenderMode,
) : NotificationSender {

    override fun supports(channel: NotificationChannel): Boolean {
        return channel in channels
    }

    override suspend fun send(command: SendNotificationCommand): SendNotificationResult {
        log.info(
            "mock notification send notificationId={} channel={} recipient={} mode={}",
            command.notificationId.id,
            command.channel,
            command.recipient,
            mode,
        )

        return when (mode) {
            MockNotificationSenderMode.SUCCESS -> SendNotificationResult.Success(
                providerMessageId = "mock-${command.notificationId.id}",
            )
            MockNotificationSenderMode.TRANSIENT_FAILURE -> SendNotificationResult.TransientFailure(
                RetryFailure.of(RetryFailureCode.VENDOR_TRANSIENT_ERROR, "mock transient failure"),
            )
            MockNotificationSenderMode.RATE_LIMITED -> SendNotificationResult.RateLimited(
                RetryFailure.of(RetryFailureCode.VENDOR_RATE_LIMITED, "mock rate limited"),
            )
            MockNotificationSenderMode.PERMANENT_FAILURE -> SendNotificationResult.PermanentFailure(
                RetryFailure.of(RetryFailureCode.INVALID_RECIPIENT, "mock permanent failure"),
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(MockNotificationSender::class.java)
    }
}
