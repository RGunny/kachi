package me.rgunny.kachi.user.application.port.inbound.subscription.model

import me.rgunny.kachi.user.domain.UserId

/**
 * 내 구독 목록 질의.
 *
 * 인증 사용자 본인의 구독만 조회하므로 조건은 userId 하나다.
 */
data class ListSubscriptionsQuery(
    val userId: UserId
)
