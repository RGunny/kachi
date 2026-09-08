package me.rgunny.kachi.collector.domain.outbox

import java.time.Instant

/**
 * PUBLISHING 상태의 소유권.
 *
 * 소유자와 점유 시각은 항상 함께 있거나 함께 없다.
 * 둘을 각각 nullable 필드로 두면 한쪽만 채워진 상태가 만들어질 수 있으므로 한 타입으로 묶는다.
 * finalize CAS의 기대값이다.
 */
data class CollectorOutboxClaim(
    val claimedBy: String,
    val claimedAt: Instant
) {
    init {
        require(claimedBy.isNotBlank()) { "outbox claimedBy는 비어 있을 수 없습니다" }
    }
}
