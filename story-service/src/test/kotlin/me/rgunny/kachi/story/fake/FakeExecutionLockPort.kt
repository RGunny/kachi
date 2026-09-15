package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutedExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockTarget

/**
 * 지정한 결과가 있으면 그것을, 없으면 실행 결과를 돌려주는 실행 lock.
 */
class FakeExecutionLockPort(
    var outcome: ExecutionLockOutcome<Nothing>? = null
) : ExecutionLockPort {

    var requestedTarget: ExecutionLockTarget? = null
        private set

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> withLock(
        target: ExecutionLockTarget,
        action: suspend () -> T
    ): ExecutionLockOutcome<T> {
        requestedTarget = target
        outcome?.let { return it as ExecutionLockOutcome<T> }

        return ExecutedExecutionLockOutcome(action())
    }
}
