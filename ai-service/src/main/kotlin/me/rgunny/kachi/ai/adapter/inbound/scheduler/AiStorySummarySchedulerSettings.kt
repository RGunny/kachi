package me.rgunny.kachi.ai.adapter.inbound.scheduler

import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesCommand
import java.time.Duration

/**
 * story 요약 scheduler가 보는 tick 설정.
 *
 * `kachi.ai.scheduler.story-summary`
 */
data class AiStorySummarySchedulerSettings(
    val enabled: Boolean,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val maxStoriesPerTick: Int
) {
    companion object {
        const val PREFIX = "kachi.ai.scheduler.story-summary"

        // @Scheduled 애노테이션 인자용 placeholder 식(상수 문자열만 허용)
        const val FIXED_DELAY_EXPRESSION = "\${$PREFIX.fixed-delay}"
        const val INITIAL_DELAY_EXPRESSION = "\${$PREFIX.initial-delay}"
    }

    fun toCommand(): SummarizeDueStoriesCommand {
        return SummarizeDueStoriesCommand(maxStories = maxStoriesPerTick)
    }
}
