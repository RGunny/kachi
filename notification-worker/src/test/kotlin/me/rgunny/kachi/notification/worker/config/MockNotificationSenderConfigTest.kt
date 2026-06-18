package me.rgunny.kachi.notification.worker.config

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.MockNotificationSenderMode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("MockNotificationSenderConfig")
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
