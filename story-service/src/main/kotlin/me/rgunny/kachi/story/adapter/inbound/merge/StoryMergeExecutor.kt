package me.rgunny.kachi.story.adapter.inbound.merge

import me.rgunny.kachi.story.application.port.inbound.merge.MergeOpenStoriesUseCase
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.application.port.outbound.lock.model.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutedExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.StoryExecutionLock
import me.rgunny.kachi.story.application.port.outbound.lock.model.UnavailableExecutionLockOutcome
import org.springframework.stereotype.Component

/**
 * 병합 요청의 중복 실행을 막고 유스케이스를 호출하는 실행기.
 *
 * lock의 결과를 이 진입점의 언어로 옮기는 일만 한다.
 */
@Component
class StoryMergeExecutor(
    private val mergeOpenStoriesUseCase: MergeOpenStoriesUseCase,
    private val executionLock: ExecutionLockPort
) {
    suspend fun execute(): StoryMergeExecution {
        val outcome = executionLock.withLock(StoryExecutionLock.STORY_MERGE) {
            mergeOpenStoriesUseCase.mergeOpenStories()
        }

        return when (outcome) {
            is ExecutedExecutionLockOutcome -> CompletedStoryMergeExecution(outcome.value)
            is AlreadyHeldExecutionLockOutcome -> AlreadyRunningStoryMergeExecution(outcome.holder)
            is UnavailableExecutionLockOutcome -> UnavailableStoryMergeExecution(outcome.cause)
        }
    }
}
