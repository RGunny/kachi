package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesResult

/**
 * story 요약 tick 수동 실행 응답.
 */
data class StorySummaryRunResponse(
    val due: Int,
    val created: Int,
    val skipped: Int,
    val failed: Int,
    val aborted: Boolean
) {
    companion object {

        fun from(result: SummarizeDueStoriesResult): StorySummaryRunResponse {
            return StorySummaryRunResponse(
                due = result.due,
                created = result.created,
                skipped = result.skipped,
                failed = result.failed,
                aborted = result.aborted
            )
        }
    }
}
