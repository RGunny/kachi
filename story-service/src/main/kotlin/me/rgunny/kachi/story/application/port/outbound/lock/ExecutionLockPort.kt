package me.rgunny.kachi.story.application.port.outbound.lock

import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockScope
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockTarget

/**
 * 같은 작업이 동시에 두 번 실행되지 않게 하는 실행 lock.
 *
 * - lock을 얻으면 action을 한 번 실행하고 [ExecutedExecutionLockOutcome]를 반환한다.
 * - 다른 실행이 쥐고 있으면 action을 실행하지 않고 [AlreadyHeldExecutionLockOutcome]를 반환한다.
 * - lock을 판단할 수 없으면 action을 실행하지 않고 [UnavailableExecutionLockOutcome]을 반환한다.
 * - action이 예외를 던지면 lock을 풀고 그 예외를 그대로 올린다.
 *
 * 임대 시간은 대상마다 설정이 정하고 호출자는 넘기지 않는다.
 * 연장은 adapter의 몫이다.
 */
interface ExecutionLockPort {

    suspend fun <T> withLock(
        target: ExecutionLockTarget,
        action: suspend () -> T
    ): ExecutionLockOutcome<T>
}
