package me.rgunny.kachi.collector.config

import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxEventType
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * outbox 이벤트를 Kafka topic으로 발행하는 설정.
 *
 * [enabled]는 발행 어댑터를 켜고 끈다. outbox를 읽어 내보내는 relay의 스위치와는 별개다.
 * 꺼져 있으면 발행 어댑터가 만들어지지 않고, 켜 둔 relay는 기동에서 실패한다.
 *
 * [retention]은 기사 이벤트 topic이 삭제 정책으로 남겨 두는 기간이다. compaction과 함께 걸려 그 기간 안에서는 기사당 최신 1건이 남는다.
 */
@ConfigurationProperties(prefix = CollectorEventsProperties.PREFIX)
data class CollectorEventsProperties(
    val enabled: Boolean,
    val topics: Topics,
    val retention: Duration
) {
    init {
        require(!retention.isNegative && !retention.isZero) { "retention은 0보다 커야 합니다" }
    }

    companion object {
        const val PREFIX = "kachi.collector.events"
    }

    data class Topics(
        val newsCollected: String
    ) {
        init {
            require(newsCollected.isNotBlank()) { "news-collected topic은 비어 있을 수 없습니다" }
        }
    }

    fun topicOf(eventType: CollectorOutboxEventType): String {
        return when (eventType) {
            CollectorOutboxEventType.NEWS_COLLECTED -> topics.newsCollected
        }
    }
}
