package me.rgunny.kachi.ai.application.port.outbound.outbox.model

import me.rgunny.kachi.ai.domain.outbox.AiOutboxEventType
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import java.time.Instant
import java.util.UUID

/**
 * 뉴스 요약이 새로 만들어졌다. 요약 본문까지 실어 소비자가 요약을 다시 읽지 않게 한다.
 */
data class SummaryCreatedEvent(
    val summaryId: UUID,
    val keyword: String,
    val title: String,
    val content: String,
    val sentiment: NewsSummarySentiment,
    val sourceNewsCount: Int,
    val provider: String,
    val model: String,
    val promptVersion: String,
    val createdAt: Instant,
    override val schemaVersion: Int = AiOutboxEvent.CURRENT_SCHEMA_VERSION
) : AiOutboxEvent {

    override val type: AiOutboxEventType
        get() = AiOutboxEventType.SUMMARY_CREATED

    override val eventKey: String
        get() = summaryId.toString()

    override val partitionKey: String
        get() = keyword

    companion object {

        fun from(summary: NewsSummary): SummaryCreatedEvent {
            return SummaryCreatedEvent(
                summaryId = summary.id.value,
                keyword = summary.keyword.value,
                title = summary.title,
                content = summary.content,
                sentiment = summary.sentiment,
                sourceNewsCount = summary.sourceNewsIds.size,
                provider = summary.provider.code,
                model = summary.model,
                promptVersion = summary.promptVersion.value,
                createdAt = summary.createdAt
            )
        }
    }
}
