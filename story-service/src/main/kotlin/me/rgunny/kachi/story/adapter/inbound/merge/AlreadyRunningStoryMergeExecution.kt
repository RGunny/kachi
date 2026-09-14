package me.rgunny.kachi.story.adapter.inbound.merge

import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockHolder

/**
 * 다른 병합이 이미 실행 중이라 이번 요청을 건너뛴 결과.
 */
data class AlreadyRunningStoryMergeExecution(
    val holder: ExecutionLockHolder
) : StoryMergeExecution
