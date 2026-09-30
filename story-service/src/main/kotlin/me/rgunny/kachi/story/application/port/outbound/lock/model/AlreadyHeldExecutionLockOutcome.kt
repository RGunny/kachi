package me.rgunny.kachi.story.application.port.outbound.lock.model

/**
 * 다른 실행이 lock을 쥐고 있어 작업을 실행하지 않은 결과.
 */
data class AlreadyHeldExecutionLockOutcome(
    val holder: ExecutionLockHolder
) : ExecutionLockOutcome<Nothing>
