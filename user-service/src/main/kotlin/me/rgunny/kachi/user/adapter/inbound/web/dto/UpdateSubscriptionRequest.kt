package me.rgunny.kachi.user.adapter.inbound.web.dto

import jakarta.validation.constraints.Size
import me.rgunny.kachi.user.domain.SubscriptionChannel

/**
 * 구독 수정 요청.
 *
 * 채널과 활성 여부를 각각 선택적으로 보낸다. 키워드 이름 변경은 없다 — 이름이 다르면 다른 키워드다.
 */
data class UpdateSubscriptionRequest(

    // 생략(null)은 "변경 없음"이고, 보냈다면 비어 있을 수 없다.
    @field:Size(min = 1)
    val channels: Set<SubscriptionChannel>? = null,

    val enabled: Boolean? = null
)
