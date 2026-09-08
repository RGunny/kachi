package me.rgunny.kachi.collector.application.port.inbound.outbox.model

import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus

/**
 * outbox 조회 조건.
 *
 * 이 API를 여는 계기가 DEAD 알림이라 기본 상태는 DEAD다.
 * 발행을 켜기 전 밀린 양을 볼 때만 PENDING을 지정한다.
 */
data class FindCollectorOutboxesQuery(
    val status: CollectorOutboxStatus = DEFAULT_STATUS,
    val limit: Int = DEFAULT_LIMIT
) {
    init {
        require(limit in 1..MAX_LIMIT) { "outbox 조회 limit은 1 이상 $MAX_LIMIT 이하여야 합니다" }
    }

    companion object {
        val DEFAULT_STATUS: CollectorOutboxStatus = CollectorOutboxStatus.DEAD
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 500
    }
}
