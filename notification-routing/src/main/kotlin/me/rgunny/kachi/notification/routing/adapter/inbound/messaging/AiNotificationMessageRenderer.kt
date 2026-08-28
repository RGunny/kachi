package me.rgunny.kachi.notification.routing.adapter.inbound.messaging

import me.rgunny.kachi.ai.contract.AiKeywordQuarantinedEvent
import me.rgunny.kachi.ai.contract.AiSummaryCreatedEvent
import org.slf4j.LoggerFactory

/**
 * ai 이벤트를 사람이 읽을 알림 본문으로 옮긴다.
 *
 * 채널별 포맷은 없다. 본문이 [MAX_LENGTH]를 넘으면 잘라내고 경고를 남긴다.
 */
object AiNotificationMessageRenderer {

    const val MAX_LENGTH = 2000
    private const val ELLIPSIS = "…"
    private val log = LoggerFactory.getLogger(AiNotificationMessageRenderer::class.java)

    fun render(event: AiSummaryCreatedEvent): String {
        val message = buildString {
            append('[').append(event.keyword).append("] ").append(event.title)
            append("\n\n").append(event.content)
            append("\n\n감성 ").append(event.sentiment.name)
            append(" · 기사 ").append(event.sourceNewsCount).append("건")
            append(" · ").append(event.createdAt)
        }
        return truncate(message, "summaryId=${event.summaryId}")
    }

    fun render(event: AiKeywordQuarantinedEvent): String {
        val message = buildString {
            append("[키워드 격리] '").append(event.keyword).append("' — ")
            append(event.targetType.name)
            append(", 연속 실패 ").append(event.consecutiveFailures).append("회")
            append(", 사유 ").append(event.lastFailureReason.name)
            append(", ").append(event.quarantinedAt)
        }
        return truncate(message, "quarantineId=${event.quarantineId}")
    }

    private fun truncate(message: String, source: String): String {
        if (message.length <= MAX_LENGTH) {
            return message
        }
        log.warn("notification message truncated {} length={} max={}", source, message.length, MAX_LENGTH)
        return message.take(MAX_LENGTH - ELLIPSIS.length) + ELLIPSIS
    }
}
