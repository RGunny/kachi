package me.rgunny.kachi.story.adapter.inbound.outbox

/**
 * lock을 확인할 수 없어 relay를 시작하지 않은 결과.
 */
data class UnavailableStoryOutboxRelayExecution(
    val cause: Throwable
) : StoryOutboxRelayExecution
