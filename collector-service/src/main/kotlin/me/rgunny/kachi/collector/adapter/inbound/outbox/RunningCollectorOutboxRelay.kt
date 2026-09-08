package me.rgunny.kachi.collector.adapter.inbound.outbox

import java.time.Instant

/**
 * lock을 쥐고 실행 중인 relay tick의 최소 메타데이터다.
 *
 * 막힌 요청에 시작 시각을 알릴 때 함께 나간다.
 */
data class RunningCollectorOutboxRelay(
    val startedAt: Instant
)
