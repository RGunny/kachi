package me.rgunny.kachi.collector.adapter.inbound.outbox

/**
 * lock을 확인할 수 없어 tick을 시작하지 않은 결과.
 *
 * 이미 실행 중이라 막힌 것과 달리 장애다.
 */
data class CollectorOutboxRelayLockUnavailable(
    val cause: Throwable
) : CollectorOutboxRelayExecutionResult
