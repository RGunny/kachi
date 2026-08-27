package me.rgunny.kachi.user.application.port.inbound.binding.model

import java.time.Instant

/**
 * 사용자가 열 `https://t.me/{bot}?start={token}` 링크와 만료 시각.
 */
data class TelegramLinkResult(
    val linkUrl: String,
    val expiresAt: Instant
)
