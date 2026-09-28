package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.inbound.story.SummarizeStoryUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.model.SkippedStorySummaryResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.StorySummarySkipReason
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeStoryCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeStoryResult

/**
 * 받은 요약 명령을 기록하고 지정한 결과를 돌려주는 fake.
 *
 * [results]에 담긴 결과를 호출 순서대로 하나씩 쓰고, 비면 [result]를 쓴다.
 * [failure]가 있으면 던진다.
 */
class RecordingSummarizeStoryUseCase : SummarizeStoryUseCase {

    val commands = mutableListOf<SummarizeStoryCommand>()
    val results = ArrayDeque<SummarizeStoryResult>()
    var result: SummarizeStoryResult = SkippedStorySummaryResult(StorySummarySkipReason.NO_PENDING)
    var failure: Throwable? = null

    override suspend fun summarize(command: SummarizeStoryCommand): SummarizeStoryResult {
        commands += command
        failure?.let { throw it }

        return results.removeFirstOrNull() ?: result
    }
}
