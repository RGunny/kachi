package me.rgunny.kachi.story.adapter.inbound.close

import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockHolder

/**
 * 다른 닫기가 이미 실행 중이라 이번 요청을 건너뛴 결과.
 */
data class AlreadyRunningStoryCloseExecution(
    val holder: ExecutionLockHolder
) : StoryCloseExecution
