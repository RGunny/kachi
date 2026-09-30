package me.rgunny.kachi.ai.config

import java.time.Duration
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesCommand
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 주기 story 요약 tick 설정.
 *
 * maxStoriesPerTick은 1 이상 [SummarizeDueStoriesCommand.MAX_STORIES_PER_TICK] 이하다.
 */
@ConfigurationProperties(prefix = AiStorySummarySchedulerProperties.PREFIX)
data class AiStorySummarySchedulerProperties(
    val enabled: Boolean,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val maxStoriesPerTick: Int
) {
    companion object {
        const val PREFIX = "kachi.ai.scheduler.story-summary"
    }

    init {
        require(maxStoriesPerTick in 1..SummarizeDueStoriesCommand.MAX_STORIES_PER_TICK) {
            "tick당 최대 story 수는 1 이상 ${SummarizeDueStoriesCommand.MAX_STORIES_PER_TICK} 이하여야 합니다: $maxStoriesPerTick"
        }
    }
}
