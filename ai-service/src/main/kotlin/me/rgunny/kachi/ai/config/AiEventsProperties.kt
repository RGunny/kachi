package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.domain.outbox.AiOutboxEventType
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * outbox 이벤트를 Kafka topic으로 발행하는 설정.
 *
 * [enabled]는 발행 어댑터를 켜고 끈다. outbox를 읽어 내보내는 relay의 스위치와는 별개다.
 * 꺼져 있으면 발행 어댑터가 만들어지지 않고, 켜 둔 relay는 기동에서 실패한다.
 *
 * topic은 이벤트 종류마다 하나다. 어느 행이 어느 topic으로 가는지는 [topicOf]가 정한다.
 */
@ConfigurationProperties(prefix = AiEventsProperties.PREFIX)
data class AiEventsProperties(
    val enabled: Boolean,
    val topics: Topics
) {
    companion object {
        const val PREFIX = "kachi.ai.events"
    }

    data class Topics(
        val summaryCreated: String,
        val keywordQuarantined: String
    ) {
        init {
            require(summaryCreated.isNotBlank()) { "summary-created topic은 비어 있을 수 없습니다" }
            require(keywordQuarantined.isNotBlank()) { "keyword-quarantined topic은 비어 있을 수 없습니다" }
        }
    }

    fun topicOf(eventType: AiOutboxEventType): String {
        return when (eventType) {
            AiOutboxEventType.SUMMARY_CREATED -> topics.summaryCreated
            AiOutboxEventType.KEYWORD_QUARANTINED -> topics.keywordQuarantined
        }
    }
}
