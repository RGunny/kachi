package me.rgunny.kachi.notification.worker.config

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.MockNotificationSenderMode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.time.Duration
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
            properties(
                mock = NotificationSenderProperties.Mock(
                    enabled = true,
                    channels = listOf("SLACK", "EMAIL"),
                    mode = MockNotificationSenderMode.SUCCESS.name,
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
            properties(
                mock = NotificationSenderProperties.Mock(
                    enabled = true,
                    channels = listOf("SLACK", "EMAIL"),
                    mode = MockNotificationSenderMode.SUCCESS.name,
                ),
                slack = slack(enabled = true),
            )
        )

        assertFalse(sender.supports(NotificationChannel.SLACK))
        assertTrue(sender.supports(NotificationChannel.EMAIL))
        assertContains(output.out, "Real notification sender is enabled while mock sender also includes channels=[SLACK]")
         assertContains(output.out, "real sender will handle those channels and mock sender support has been disabled")
        assertContains(output.out, "effectiveMockChannels=[EMAIL]")
    }

    @Test
    @DisplayName("실제 Discord sender가 enabled이면 mock sender의 DISCORD 지원을 제외한다")
    fun removeDiscordFromMockChannelsWhenDiscordSenderEnabled(output: CapturedOutput) {
        val sender = config.mockNotificationSender(
            properties(
                mock = NotificationSenderProperties.Mock(
                    enabled = true,
                    channels = listOf("DISCORD", "EMAIL"),
                    mode = MockNotificationSenderMode.SUCCESS.name,
                ),
                discord = discord(enabled = true),
            )
        )

        assertFalse(sender.supports(NotificationChannel.DISCORD))
        assertTrue(sender.supports(NotificationChannel.EMAIL))
        assertContains(output.out, "Real notification sender is enabled while mock sender also includes channels=[DISCORD]")
        assertContains(output.out, "real sender will handle those channels and mock sender support has been disabled")
        assertContains(output.out, "effectiveMockChannels=[EMAIL]")
    }

    @Test
    @DisplayName("실제 Telegram sender가 enabled이면 mock sender의 TELEGRAM 지원을 제외한다")
    fun removeTelegramFromMockChannelsWhenTelegramSenderEnabled(output: CapturedOutput) {
        val sender = config.mockNotificationSender(
            properties(
                mock = NotificationSenderProperties.Mock(
                    enabled = true,
                    channels = listOf("TELEGRAM", "EMAIL"),
                    mode = MockNotificationSenderMode.SUCCESS.name,
                ),
                telegram = telegram(enabled = true),
            )
        )

        assertFalse(sender.supports(NotificationChannel.TELEGRAM))
        assertTrue(sender.supports(NotificationChannel.EMAIL))
        assertContains(output.out, "Real notification sender is enabled while mock sender also includes channels=[TELEGRAM]")
        assertContains(output.out, "real sender will handle those channels and mock sender support has been disabled")
        assertContains(output.out, "effectiveMockChannels=[EMAIL]")
    }

    @Test
    @DisplayName("지원하지 않는 channel 설정은 거부한다")
    fun rejectInvalidChannel() {
        assertFailsWith<IllegalArgumentException> {
            config.mockNotificationSender(
                properties(
                    mock = NotificationSenderProperties.Mock(
                        enabled = true,
                        channels = listOf("UNKNOWN"),
                        mode = MockNotificationSenderMode.SUCCESS.name,
                    )
                )
            )
        }
    }

    private fun properties(
        mock: NotificationSenderProperties.Mock,
        slack: NotificationSenderProperties.Slack = slack(enabled = false),
        discord: NotificationSenderProperties.Discord = discord(enabled = false),
        telegram: NotificationSenderProperties.Telegram = telegram(enabled = false),
    ): NotificationSenderProperties {
        return NotificationSenderProperties(
            mock = mock,
            slack = slack,
            discord = discord,
            telegram = telegram,
        )
    }

    private fun slack(enabled: Boolean): NotificationSenderProperties.Slack {
        return NotificationSenderProperties.Slack(
            enabled = enabled,
            webhookUrl = "https://hooks.slack.test/services/test",
            connectTimeout = Duration.ofSeconds(2),
            responseTimeout = Duration.ofSeconds(5),
            readTimeout = Duration.ofSeconds(5),
            writeTimeout = Duration.ofSeconds(5),
            maxInMemorySize = 256 * 1024,
        )
    }

    private fun discord(enabled: Boolean): NotificationSenderProperties.Discord {
        return NotificationSenderProperties.Discord(
            enabled = enabled,
            webhookUrl = "https://discord.test/api/webhooks/test",
            connectTimeout = Duration.ofSeconds(2),
            responseTimeout = Duration.ofSeconds(5),
            readTimeout = Duration.ofSeconds(5),
            writeTimeout = Duration.ofSeconds(5),
            maxInMemorySize = 256 * 1024,
        )
    }

    private fun telegram(enabled: Boolean): NotificationSenderProperties.Telegram {
        return NotificationSenderProperties.Telegram(
            enabled = enabled,
            baseUrl = "https://api.telegram.test",
            botToken = "telegram-bot-token",
            sendMessagePath = "/sendMessage",
            connectTimeout = Duration.ofSeconds(2),
            responseTimeout = Duration.ofSeconds(5),
            readTimeout = Duration.ofSeconds(5),
            writeTimeout = Duration.ofSeconds(5),
            maxInMemorySize = 256 * 1024,
        )
    }
}
