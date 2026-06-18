package me.rgunny.kachi.notification.worker.config

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.MockNotificationSender
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.MockNotificationSenderMode
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * mock sender adapter 설정.
 *
 * sender adapter가 없으면 worker는 dispatch 메시지를 consume해도 실제 발송 단계에서 sender not found로 끝난다.
 * 초기 런타임 검증에서는 mock sender를 켜서 상태 전이와 retry classification을 먼저 확인하고,
 * 실제 Slack/Discord/Telegram adapter가 붙으면 운영 profile에서는 이 설정을 끈다.
 */
@Configuration
class MockNotificationSenderConfig {

    @Bean
    @ConditionalOnProperty(
        prefix = "kachi.notification.worker.sender.mock",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun mockNotificationSender(properties: NotificationWorkerProperties): MockNotificationSender {
        val mock = properties.sender.mock
        val channels = mock.channels.map { channel ->
            NotificationChannel.valueOf(channel.uppercase())
        }.toSet()
        val mode = MockNotificationSenderMode.valueOf(mock.mode.uppercase())

        require(channels.isNotEmpty()) { "mock sender channels must not be empty" }

        return MockNotificationSender(
            channels = channels,
            mode = mode,
        )
    }
}
