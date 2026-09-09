package me.rgunny.kachi.notification.service.adapter.outbound.persistence.outbox

import java.time.Instant
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * notification dispatch outbox MongoDB 저장 모델.
 */
@Document("notification_outboxes")
@CompoundIndexes(
    CompoundIndex(
        name = "idx_notification_outboxes_publishable",
        def = "{'outboxStatus': 1, 'nextRetryAt': 1, 'createdAt': 1}"
    ),
    CompoundIndex(
        name = "idx_notification_outboxes_stale_publishing",
        def = "{'outboxStatus': 1, 'claimedAt': 1}"
    )
)
data class NotificationOutboxDocument(
    @Id
    val id: String,
    @Indexed(name = "idx_notification_outboxes_notification_id")
    val notificationId: String,
    val topic: String,
    val partitionKey: String,
    val eventPayload: String,
    val createdAt: Instant,
    val outboxStatus: String,
    val retryCount: Int,
    val nextRetryAt: Instant,
    val lastError: String?,
    val publishedAt: Instant?,
    val claimedAt: Instant?,
    val claimedBy: String?,
)
