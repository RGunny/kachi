package me.rgunny.kachi.collector.domain.outbox

/**
 * outbox 행의 발행 상태.
 *
 * PUBLISHED와 DEAD가 종료 상태이고, DEAD만 운영자 복구로 PENDING에 돌아온다.
 */
enum class CollectorOutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    DEAD
}
