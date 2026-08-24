package me.rgunny.kachi.ai.adapter.inbound.outbox

import java.time.Instant

/**
 * 현재 인스턴스에서 실행 중인 relay tick의 최소 메타데이터다.
 *
 * executor가 중복 실행을 막으려고 메모리에 들고 있는 상태이며, 막힌 요청에 시작 시각을 알릴 때 함께 나간다.
 */
data class RunningAiOutboxRelay(
    val startedAt: Instant
)
