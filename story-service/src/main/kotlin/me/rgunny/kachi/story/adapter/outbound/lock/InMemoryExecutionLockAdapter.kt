package me.rgunny.kachi.story.adapter.outbound.lock

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import me.rgunny.kachi.story.application.port.outbound.lock.model.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutedExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockHolder
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockTarget

/**
 * 이 인스턴스 안에서만 유효한 실행 lock.
 */
class InMemoryExecutionLockAdapter(
    private val clock: Clock
) : ExecutionLockPort {

    private val holders = ConcurrentHashMap<String, ExecutionLockHolder>()

    override suspend fun <T> withLock(
        target: ExecutionLockTarget,
        action: suspend () -> T
    ): ExecutionLockOutcome<T> {
        // 확인과 채움이 한 연산이라 동시 요청 중 하나만 통과한다.
        val holder = ExecutionLockHolder(acquiredAt = Instant.now(clock), owner = LOCAL_OWNER)
        val currentHolder = holders.putIfAbsent(target.key, holder)
        if (currentHolder != null) {
            return AlreadyHeldExecutionLockOutcome(currentHolder)
        }

        return try {
            ExecutedExecutionLockOutcome(action())
        } finally {
            // 예외로 끝나도 풀되, 내가 넣은 값일 때만 지운다.
            holders.remove(target.key, holder)
        }
    }

    private companion object {
        const val LOCAL_OWNER = "this-instance"
    }
}
