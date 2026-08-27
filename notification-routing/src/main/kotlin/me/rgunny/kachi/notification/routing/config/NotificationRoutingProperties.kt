package me.rgunny.kachi.notification.routing.config

import me.rgunny.kachi.notification.contract.NotificationChannel
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * ai 이벤트 consumer, 접수 topic producer, user-service 클라이언트 설정.
 *
 * user-service base-url이 없으면 기동 시 실패한다.
 */
@ConfigurationProperties(prefix = NotificationRoutingProperties.PREFIX)
data class NotificationRoutingProperties(
    val groupId: String,
    val requestTopic: String,
    val autoOffsetReset: String,
    val topics: Topics,
    val dlt: Dlt,
    val retry: Retry,
    val admin: Admin,
    val userService: UserService,
) {
    companion object {
        const val PREFIX = "kachi.notification.routing"
        const val NO_USER_SERVICE_BASE_URL_MESSAGE =
            "user-service base-url이 없습니다. `$PREFIX.user-service.base-url`을 지정하십시오"
    }

    init {
        require(groupId.isNotBlank()) { "group-id는 비어 있을 수 없습니다" }
        require(requestTopic.isNotBlank()) { "request-topic은 비어 있을 수 없습니다" }
        require(autoOffsetReset.isNotBlank()) { "auto-offset-reset은 비어 있을 수 없습니다" }
        require(userService.baseUrl.isNotBlank()) { NO_USER_SERVICE_BASE_URL_MESSAGE }
    }

    data class Topics(
        val summaryCreated: String,
        val keywordQuarantined: String,
    ) {
        init {
            require(summaryCreated.isNotBlank()) { "summary-created topic은 비어 있을 수 없습니다" }
            require(keywordQuarantined.isNotBlank()) { "keyword-quarantined topic은 비어 있을 수 없습니다" }
        }
    }

    data class Dlt(
        val topic: String,
    ) {
        init {
            require(topic.isNotBlank()) { "dlt topic은 비어 있을 수 없습니다" }
        }
    }

    data class Retry(
        val maxAttempts: Long,
        val backoff: Duration,
    ) {
        init {
            require(maxAttempts >= 1) { "retry.max-attempts는 1 이상이어야 합니다" }
            require(!backoff.isNegative && !backoff.isZero) { "retry.backoff는 0보다 커야 합니다" }
        }
    }

    /**
     * 관리자 알림 수신처. 값은 주소가 아니라 수신처 참조이며 비어 있어도 된다.
     */
    data class Admin(
        val recipients: Map<NotificationChannel, String>,
    ) {
        init {
            recipients.forEach { (channel, recipient) ->
                require(recipient.isNotBlank()) { "admin.recipients.${channel.name.lowercase()}는 비어 있을 수 없습니다" }
            }
        }
    }

    data class UserService(
        val baseUrl: String,
        val subscriptionsPath: String,
        val timeout: Duration,
        val maxInMemorySize: Int,
    ) {
        init {
            require(subscriptionsPath.isNotBlank()) { "user-service.subscriptions-path는 비어 있을 수 없습니다" }
            require(!timeout.isNegative && !timeout.isZero) { "user-service.timeout은 0보다 커야 합니다" }
            require(maxInMemorySize > 0) { "user-service.max-in-memory-size는 0보다 커야 합니다" }
        }
    }
}
