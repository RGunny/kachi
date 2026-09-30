package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.inbound.story.SummarizeDueStoriesUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesResult

/**
 * 받은 tick 명령을 기록하고 지정한 결과를 돌려주는 fake.
 */
class RecordingSummarizeDueStoriesUseCase : SummarizeDueStoriesUseCase {

    val commands = mutableListOf<SummarizeDueStoriesCommand>()
    var result: SummarizeDueStoriesResult =
        SummarizeDueStoriesResult(due = 0, created = 0, skipped = 0, failed = 0, aborted = false)
    var failure: Throwable? = null

    override suspend fun summarizeDue(command: SummarizeDueStoriesCommand): SummarizeDueStoriesResult {
        commands += command
        failure?.let { throw it }

        return result
    }
}
