package me.rgunny.kachi.notification.worker.adapter.outbound.sender

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationResult
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.retry.RetryFailureCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@DisplayName("MockNotificationSender")
class MockNotificationSenderTest {

    @Test
    @DisplayName("설정된 채널만 지원한다")
    fun supportsConfiguredChannels() {
        val sender = MockNotificationSender(
            channels = setOf(NotificationChannel.SLACK),
            mode = MockNotificationSenderMode.SUCCESS,
        )

        assertTrue(sender.supports(NotificationChannel.SLACK))
        assertFalse(sender.supports(NotificationChannel.EMAIL))
    }

    @Test
    @DisplayName("SUCCESS 모드는 성공 결과를 반환한다")
    fun success() = runBlocking {
        val sender = MockNotificationSender(
            channels = setOf(NotificationChannel.SLACK),
            mode = MockNotificationSenderMode.SUCCESS,
        )

        val result = sender.send(command())

        assertIs<SendNotificationResult.Success>(result)
        Unit
    }

    @Test
    @DisplayName("TRANSIENT_FAILURE 모드는 retryable transient failure를 반환한다")
    fun transientFailure() = runBlocking {
        val sender = MockNotificationSender(
            channels = setOf(NotificationChannel.SLACK),
            mode = MockNotificationSenderMode.TRANSIENT_FAILURE,
        )

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.TransientFailure>(result)
        assertEquals(RetryFailureCode.VENDOR_TRANSIENT_ERROR.code, failure.failure.code)
    }

    @Test
    @DisplayName("RATE_LIMITED 모드는 retryable rate limit failure를 반환한다")
    fun rateLimited() = runBlocking {
        val sender = MockNotificationSender(
            channels = setOf(NotificationChannel.SLACK),
            mode = MockNotificationSenderMode.RATE_LIMITED,
        )

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.RateLimited>(result)
        assertEquals(RetryFailureCode.VENDOR_RATE_LIMITED.code, failure.failure.code)
    }

    @Test
    @DisplayName("PERMANENT_FAILURE 모드는 non-retryable permanent failure를 반환한다")
    fun permanentFailure() = runBlocking {
        val sender = MockNotificationSender(
            channels = setOf(NotificationChannel.SLACK),
            mode = MockNotificationSenderMode.PERMANENT_FAILURE,
        )

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.PermanentFailure>(result)
        assertEquals(RetryFailureCode.INVALID_RECIPIENT.code, failure.failure.code)
    }

    private fun command(): SendNotificationCommand {
        return SendNotificationCommand(
            notificationId = NotificationId.newId(),
            channel = NotificationChannel.SLACK,
            address = "https://hooks.slack.test/services/test",
            message = "hello",
            idempotencyKey = "idempotency-key",
        )
    }
}
