package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.outbound.lock.ExecutedExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockTarget

/**
 * 정해 둔 결과만 돌려주는 실행 lock.
 */
class FakeExecutionLockPort(
    private val outcome: ExecutionLockOutcome<Nothing>
) : ExecutionLockPort {

    var requestedTarget: ExecutionLockTarget? = null
        private set

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> withLock(
        target: ExecutionLockTarget,
        action: suspend () -> T
    ): ExecutionLockOutcome<T> {
        requestedTarget = target

        return outcome as ExecutionLockOutcome<T>
    }
}
