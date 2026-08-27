package me.rgunny.kachi.user.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Telegram 연결 링크 설정.
 */
@ConfigurationProperties(prefix = "kachi.user.telegram")
data class TelegramProperties(
    /** 연결 링크 `https://t.me/{bot-username}?start=` 에 들어가는 봇 계정명. */
    val botUsername: String
)
