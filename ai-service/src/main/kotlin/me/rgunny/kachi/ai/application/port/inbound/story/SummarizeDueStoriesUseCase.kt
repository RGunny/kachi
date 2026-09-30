package me.rgunny.kachi.ai.application.port.inbound.story

import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesResult

/**
 * maxWait를 넘긴 story들을 찾아 순서대로 요약하는 유스케이스.
 */
interface SummarizeDueStoriesUseCase {

    suspend fun summarizeDue(command: SummarizeDueStoriesCommand): SummarizeDueStoriesResult
}
