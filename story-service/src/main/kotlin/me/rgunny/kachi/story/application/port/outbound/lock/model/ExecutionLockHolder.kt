package me.rgunny.kachi.story.application.port.outbound.lock.model

import java.time.Instant

/**
 * lock을 쥐고 있는 실행.
 */
data class ExecutionLockHolder(
    val acquiredAt: Instant,
    val owner: String
)
