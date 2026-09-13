package me.rgunny.kachi.story.application.port.inbound.close.model

import java.time.Instant

/**
 * 닫기 한 번의 결과.
 *
 * [conflictedCount]는 닫는 사이 기사가 붙어 건너뛴 story 수.
 * [indexDeleteFailureCount]는 닫혔지만 벡터 삭제가 실패한 story 수.
 */
data class CloseIdleStoriesResult(
    val threshold: Instant,
    val closedCount: Int,
    val conflictedCount: Int,
    val indexDeleteFailureCount: Int
)
