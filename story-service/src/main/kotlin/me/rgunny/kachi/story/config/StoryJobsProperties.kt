package me.rgunny.kachi.story.config

import java.time.Duration
import me.rgunny.kachi.story.application.service.close.StoryClosePolicy
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 주기 작업 설정.
 */
@ConfigurationProperties(prefix = StoryJobsProperties.PREFIX)
data class StoryJobsProperties(
    val close: Close,
    val cleanup: Cleanup
) {
    companion object {
        const val PREFIX = "kachi.story.jobs"
    }

    data class Close(
        val enabled: Boolean,
        val interval: Duration,
        val initialDelay: Duration,
        val closeAfter: Duration,
        val batchLimit: Int
    ) {
        init {
            require(!interval.isNegative && !interval.isZero) { "close.interval은 양수여야 합니다: $interval" }
            require(!initialDelay.isNegative) { "close.initial-delay는 음수일 수 없습니다: $initialDelay" }
        }

        fun toPolicy(): StoryClosePolicy {
            return StoryClosePolicy(closeAfter = closeAfter, batchLimit = batchLimit)
        }
    }

    data class Cleanup(
        val enabled: Boolean,
        val interval: Duration,
        val initialDelay: Duration
    ) {
        init {
            require(!interval.isNegative && !interval.isZero) { "cleanup.interval은 양수여야 합니다: $interval" }
            require(!initialDelay.isNegative) { "cleanup.initial-delay는 음수일 수 없습니다: $initialDelay" }
        }
    }
}
