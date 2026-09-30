package me.rgunny.kachi.story.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * outbox 이벤트를 Kafka topic으로 발행하는 설정.
 *
 * [enabled]는 발행 어댑터의 스위치다. outbox 행을 읽어 내보내는 relay의 스위치와 별개이며, 꺼진 채 relay를 켜면 기동에 실패한다.
 * [retention]은 두 topic이 delete 정책으로 레코드를 남겨 두는 기간이다.
 */
@ConfigurationProperties(prefix = StoryEventsProperties.PREFIX)
data class StoryEventsProperties(
    val enabled: Boolean,
    val topics: Topics,
    val retention: Duration
) {
    companion object {
        const val PREFIX = "kachi.story.events"
    }

    init {
        require(!retention.isNegative && !retention.isZero) { "events retention은 양수여야 합니다: $retention" }
    }

    data class Topics(
        val articleAttached: String,
        val merged: String
    ) {
        init {
            require(articleAttached.isNotBlank()) { "article-attached topic은 비어 있을 수 없습니다" }
            require(merged.isNotBlank()) { "merged topic은 비어 있을 수 없습니다" }
        }
    }
}
