package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.domain.SubscriptionChannel
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 로컬 시드 설정. 시드 관리자에게 심을 채널별 수신 주소.
 */
@ConfigurationProperties(prefix = "kachi.user.seed")
data class UserSeedProperties(
    val adminAddresses: AdminAddresses = AdminAddresses()
) {

    /** 채널별 주소. 비어 있으면 그 채널은 형식만 맞춘 자리표시 주소로 심는다. */
    data class AdminAddresses(
        val slack: String = "",
        val discord: String = "",
        val telegram: String = ""
    ) {
        /** 설정된 주소. 공백뿐이면 null이다. */
        fun of(channel: SubscriptionChannel): String? {
            val value = when (channel) {
                SubscriptionChannel.SLACK -> slack
                SubscriptionChannel.DISCORD -> discord
                SubscriptionChannel.TELEGRAM -> telegram
            }
            return value.trim().ifEmpty { null }
        }
    }
}
