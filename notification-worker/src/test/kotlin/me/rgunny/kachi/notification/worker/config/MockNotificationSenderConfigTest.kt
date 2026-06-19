package me.rgunny.kachi.notification.worker.config

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.MockNotificationSenderMode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("MockNotificationSenderConfig")
@ExtendWith(OutputCaptureExtension::class)
class MockNotificationSenderConfigTest {

    private val config = MockNotificationSenderConfig()

    @Test
    @DisplayName("properties의 channel/mode를 mock sender로 변환한다")
    fun mockNotificationSender() {
        val sender = config.mockNotificationSender(
            NotificationWorkerProperties(
                sender = NotificationWorkerProperties.Sender(
                    mock = NotificationWorkerProperties.Sender.Mock(
                        channels = listOf("SLACK", "EMAIL"),
                        mode = MockNotificationSenderMode.SUCCESS.name,
                    )
                )
            )
        )

        assertTrue(sender.supports(NotificationChannel.SLACK))
        assertTrue(sender.supports(NotificationChannel.EMAIL))
    }

    @Test
    @DisplayName("실제 Slack sender가 enabled이면 mock sender의 SLACK 지원을 제외한다")
    fun removeSlackFromMockChannelsWhenSlackSenderEnabled(output: CapturedOutput) {
        val sender = config.mockNotificationSender(
            NotificationWorkerProperties(
                sender = NotificationWorkerProperties.Sender(
                    mock = NotificationWorkerProperties.Sender.Mock(
                        channels = listOf("SLACK", "EMAIL"),
                        mode = MockNotificationSenderMode.SUCCESS.name,
                    ),
                    slack = NotificationWorkerProperties.Sender.Slack(
                        enabled = true,
                        webhookUrl = "https://hooks.slack.test/services/test",
                    )
                )
            )
        )

        assertFalse(sender.supports(NotificationChannel.SLACK))
        assertTrue(sender.supports(NotificationChannel.EMAIL))
        assertContains(output.out, "Real notification sender is enabled while mock sender also includes channels=[SLACK]")
        assertContains(output.out, "real sender will handle those channels and mock sender support has been disabled")
        assertContains(output.out, "effectiveMockChannels=[EMAIL]")
    }

    @Test
    @DisplayName("지원하지 않는 channel 설정은 거부한다")
    fun rejectInvalidChannel() {
        assertFailsWith<IllegalArgumentException> {
            config.mockNotificationSender(
                NotificationWorkerProperties(
                    sender = NotificationWorkerProperties.Sender(
                        mock = NotificationWorkerProperties.Sender.Mock(
                            channels = listOf("UNKNOWN"),
                        )
                    )
                )
            )
        }
    }
}
