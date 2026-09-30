package me.rgunny.kachi.story.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 기사 이벤트 consumer 설정.
 *
 * [maxPollRecords]는 poll 한 번에 받는 레코드 수다. 한 poll의 처리 시간과 [Retry]의 `max-backoff`를 합쳐 `max.poll.interval.ms` 안에 들어야 한다.
 * [Retry]는 같은 offset을 다시 처리하는 간격이며 상한 횟수는 없다.
 */
@ConfigurationProperties(prefix = StoryConsumerProperties.PREFIX)
data class StoryConsumerProperties(
    val groupId: String,
    val autoOffsetReset: String,
    val concurrency: Int,
    val maxPollRecords: Int,
    val topics: Topics,
    val dlt: Dlt,
    val retry: Retry
) {
    companion object {
        const val PREFIX = "kachi.story.consumer"
    }

    init {
        require(groupId.isNotBlank()) { "group-id는 비어 있을 수 없습니다" }
        require(autoOffsetReset.isNotBlank()) { "auto-offset-reset은 비어 있을 수 없습니다" }
        require(concurrency >= 1) { "concurrency는 1 이상이어야 합니다: $concurrency" }
        require(maxPollRecords >= 1) { "max-poll-records는 1 이상이어야 합니다: $maxPollRecords" }
    }

    data class Topics(
        val newsCollected: String
    ) {
        init {
            require(newsCollected.isNotBlank()) { "news-collected topic은 비어 있을 수 없습니다" }
        }
    }

    data class Dlt(
        val topic: String
    ) {
        init {
            require(topic.isNotBlank()) { "dlt topic은 비어 있을 수 없습니다" }
        }
    }

    data class Retry(
        val initialBackoff: Duration,
        val maxBackoff: Duration,
        val multiplier: Double
    ) {
        init {
            require(!initialBackoff.isNegative && !initialBackoff.isZero) { "retry.initial-backoff는 0보다 커야 합니다" }
            require(maxBackoff >= initialBackoff) { "retry.max-backoff는 initial-backoff 이상이어야 합니다" }
            require(multiplier >= 1.0) { "retry.multiplier는 1 이상이어야 합니다: $multiplier" }
        }
    }
}
