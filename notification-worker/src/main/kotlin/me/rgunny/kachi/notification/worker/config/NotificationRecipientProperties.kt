package me.rgunny.kachi.notification.worker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 수신 주소 조회 설정. 캐시 TTL과 user-service 클라이언트다.
 *
 * user-service base-url이 없으면 기동 시 실패한다.
 */
@ConfigurationProperties(prefix = NotificationRecipientProperties.PREFIX)
data class NotificationRecipientProperties(
    val cacheTtl: Duration,
    val userService: UserService,
) {
    companion object {
        const val PREFIX = "kachi.notification.recipient"
        const val NO_USER_SERVICE_BASE_URL_MESSAGE =
            "user-service base-url이 없습니다. `$PREFIX.user-service.base-url`을 지정하십시오"
        const val USER_ID_PLACEHOLDER = "{userId}"
        const val CHANNEL_PLACEHOLDER = "{channel}"
    }

    init {
        require(!cacheTtl.isNegative && !cacheTtl.isZero) { "cache-ttl은 0보다 커야 합니다" }
        require(userService.baseUrl.isNotBlank()) { NO_USER_SERVICE_BASE_URL_MESSAGE }
    }

    data class UserService(
        val baseUrl: String,
        val channelBindingPath: String,
        val timeout: Duration,
        val maxInMemorySize: Int,
    ) {
        init {
            require(channelBindingPath.contains(USER_ID_PLACEHOLDER) && channelBindingPath.contains(CHANNEL_PLACEHOLDER)) {
                "user-service.channel-binding-path에는 $USER_ID_PLACEHOLDER 와 $CHANNEL_PLACEHOLDER 가 있어야 합니다"
            }
            require(!timeout.isNegative && !timeout.isZero) { "user-service.timeout은 0보다 커야 합니다" }
            require(maxInMemorySize > 0) { "user-service.max-in-memory-size는 0보다 커야 합니다" }
        }
    }
}
