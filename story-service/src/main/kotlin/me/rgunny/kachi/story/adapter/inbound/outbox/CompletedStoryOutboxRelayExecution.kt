package me.rgunny.kachi.story.adapter.inbound.outbox

import me.rgunny.kachi.story.application.port.inbound.outbox.model.RelayStoryOutboxResult

/**
 * lock을 획득해 relay tick 한 번이 끝난 결과.
 */
data class CompletedStoryOutboxRelayExecution(
    val result: RelayStoryOutboxResult
) : StoryOutboxRelayExecution
