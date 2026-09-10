package me.rgunny.kachi.story.application.port.outbound.lock

/**
 * lock을 확인할 수 없어 작업을 실행하지 않은 결과.
 */
data class UnavailableExecutionLockOutcome(
    val cause: Throwable
) : ExecutionLockOutcome<Nothing>
