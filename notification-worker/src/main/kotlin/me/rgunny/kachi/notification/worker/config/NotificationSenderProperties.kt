package me.rgunny.kachi.notification.worker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * notification sender adapter 설정.
 */
@ConfigurationProperties(prefix = "kachi.notification.sender")
data class NotificationSenderProperties(
    val mock: Mock,
    val slack: Slack,
    val discord: Discord,
    val telegram: Telegram,
) {

    data class Mock(
        /**
         * 실제 vendor adapter가 붙기 전 worker dispatch 흐름을 검증하기 위한 mock sender 활성화 여부.
         * 운영 환경에서는 false로 두고 실제 채널 sender bean만 사용해야 한다.
         */
        val enabled: Boolean,

        /**
         * mock sender가 지원할 채널 목록.
         */
        val channels: List<String>,

        /**
         * mock sender 결과 모드.
         * SUCCESS, TRANSIENT_FAILURE, RATE_LIMITED, PERMANENT_FAILURE 중 하나를 사용한다.
         */
        val mode: String,
    )

    data class Slack(
        /**
         * Slack incoming webhook sender 활성화 여부.
         * true이면 mock sender가 SLACK을 지원하도록 설정되어 있어도 worker config가 mock SLACK 지원을 자동 제외한다.
         */
        val enabled: Boolean,
        val webhookUrl: String?,
        val connectTimeout: Duration,
        val responseTimeout: Duration,
        val readTimeout: Duration,
        val writeTimeout: Duration,
        val maxInMemorySize: Int,
    )

    data class Discord(
        /**
         * Discord incoming webhook sender 활성화 여부.
         * true이면 mock sender가 DISCORD를 지원하도록 설정되어 있어도 worker config가 mock DISCORD 지원을 자동 제외한다.
         */
        val enabled: Boolean,
        val webhookUrl: String?,
        val connectTimeout: Duration,
        val responseTimeout: Duration,
        val readTimeout: Duration,
        val writeTimeout: Duration,
        val maxInMemorySize: Int,
    )

    data class Telegram(
        /**
         * Telegram Bot API sender 활성화 여부.
         * true이면 mock sender가 TELEGRAM을 지원하도록 설정되어 있어도 worker config가 mock TELEGRAM 지원을 자동 제외한다.
         */
        val enabled: Boolean,
        val baseUrl: String,
        val botToken: String?,
        val sendMessagePath: String,
        val connectTimeout: Duration,
        val responseTimeout: Duration,
        val readTimeout: Duration,
        val writeTimeout: Duration,
        val maxInMemorySize: Int,
    )
}
