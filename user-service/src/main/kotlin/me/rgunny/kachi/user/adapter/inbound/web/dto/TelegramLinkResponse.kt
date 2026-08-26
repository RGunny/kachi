package me.rgunny.kachi.user.adapter.inbound.web.dto

import me.rgunny.kachi.user.application.port.inbound.binding.model.TelegramLinkResult
import java.time.Instant

/**
 * 연결 링크 응답. 사용자가 열 링크와 그 만료 시각이다.
 */
data class TelegramLinkResponse(
    val linkUrl: String,
    val expiresAt: Instant
) {

    companion object {

        fun from(result: TelegramLinkResult): TelegramLinkResponse {
            return TelegramLinkResponse(linkUrl = result.linkUrl, expiresAt = result.expiresAt)
        }
    }
}
