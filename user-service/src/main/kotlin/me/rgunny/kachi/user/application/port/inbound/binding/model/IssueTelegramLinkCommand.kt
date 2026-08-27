package me.rgunny.kachi.user.application.port.inbound.binding.model

import me.rgunny.kachi.user.domain.UserId

/**
 * 연결 링크를 받을 사용자. 채널은 유스케이스가 정한다.
 */
data class IssueTelegramLinkCommand(
    val userId: UserId
)
