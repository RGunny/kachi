package me.rgunny.kachi.story.adapter.inbound.close

import me.rgunny.kachi.story.application.port.inbound.close.CloseIdleStoriesUseCase
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.application.port.outbound.lock.model.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutedExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.StoryExecutionLock
import me.rgunny.kachi.story.application.port.outbound.lock.model.UnavailableExecutionLockOutcome
import org.springframework.stereotype.Component

/**
 * 닫기 요청의 중복 실행을 막고 유스케이스를 호출하는 executor.
 *
 * lock의 결과를 이 진입점의 언어로 옮기는 일만 한다.
 */
@Component
class StoryCloseExecutor(
    private val closeIdleStoriesUseCase: CloseIdleStoriesUseCase,
    private val executionLock: ExecutionLockPort
) {
    suspend fun execute(): StoryCloseExecution {
        val outcome = executionLock.withLock(StoryExecutionLock.STORY_CLOSE) {
            closeIdleStoriesUseCase.closeIdleStories()
        }

        return when (outcome) {
            is ExecutedExecutionLockOutcome -> CompletedStoryCloseExecution(outcome.value)
            is AlreadyHeldExecutionLockOutcome -> AlreadyRunningStoryCloseExecution(outcome.holder)
            is UnavailableExecutionLockOutcome -> UnavailableStoryCloseExecution(outcome.cause)
        }
    }
}
