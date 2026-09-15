package me.rgunny.kachi.story.adapter.inbound.cleanup

import me.rgunny.kachi.story.application.port.inbound.cleanup.CleanupCandidateIndexUseCase
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.application.port.outbound.lock.model.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutedExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.StoryExecutionLock
import me.rgunny.kachi.story.application.port.outbound.lock.model.UnavailableExecutionLockOutcome
import org.springframework.stereotype.Component

/**
 * 색인 정리 요청의 중복 실행을 막고 유스케이스를 호출하는 executor.
 *
 * lock의 결과를 이 진입점의 언어로 옮기는 일만 한다.
 */
@Component
class IndexCleanupExecutor(
    private val cleanupCandidateIndexUseCase: CleanupCandidateIndexUseCase,
    private val executionLock: ExecutionLockPort
) {
    suspend fun execute(): IndexCleanupExecution {
        val outcome = executionLock.withLock(StoryExecutionLock.INDEX_CLEANUP) {
            cleanupCandidateIndexUseCase.cleanup()
        }

        return when (outcome) {
            is ExecutedExecutionLockOutcome -> CompletedIndexCleanupExecution(outcome.value)
            is AlreadyHeldExecutionLockOutcome -> AlreadyRunningIndexCleanupExecution(outcome.holder)
            is UnavailableExecutionLockOutcome -> UnavailableIndexCleanupExecution(outcome.cause)
        }
    }
}
