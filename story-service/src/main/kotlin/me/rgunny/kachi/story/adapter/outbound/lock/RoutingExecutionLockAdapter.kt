package me.rgunny.kachi.story.adapter.outbound.lock

import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockScope
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockTarget

/**
 * 실행 단위가 선언한 범위에 맞는 lock 구현으로 넘기는 라우터.
 */
class RoutingExecutionLockAdapter(
    private val delegates: Map<ExecutionLockScope, ExecutionLockPort>
) : ExecutionLockPort {

    init {
        val unmapped = ExecutionLockScope.entries.filterNot { it in delegates }

        require(unmapped.isEmpty()) {
            "lock 구현이 연결되지 않은 범위가 있습니다: $unmapped"
        }
    }

    override suspend fun <T> withLock(
        target: ExecutionLockTarget,
        action: suspend () -> T
    ): ExecutionLockOutcome<T> {
        return delegates.getValue(target.scope).withLock(target, action)
    }
}
