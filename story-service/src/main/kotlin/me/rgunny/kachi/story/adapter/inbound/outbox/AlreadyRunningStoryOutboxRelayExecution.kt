package me.rgunny.kachi.story.adapter.inbound.outbox

import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockHolder

/**
 * 다른 relay가 이미 실행 중이라 이번 요청을 건너뛴 결과.
 */
data class AlreadyRunningStoryOutboxRelayExecution(
    val holder: ExecutionLockHolder
) : StoryOutboxRelayExecution
