package me.rgunny.kachi.story.application.port.outbound.lock.model

/**
 * lock을 획득해 작업이 실행된 결과.
 */
data class ExecutedExecutionLockOutcome<T>(
    val value: T
) : ExecutionLockOutcome<T>
