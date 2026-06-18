package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * worker가 읽고 갱신하는 notifications MongoDB 저장 모델.
 */
@Document("notifications")
data class NotificationDocument(
    @Id
    val id: String,
    val requestId: String,
    val requester: String,
    val channel: String,
    val recipient: String,
    val message: String?,
    val requestedAt: Instant,
    val status: String,
    val failureReason: String?,
    val updatedAt: Instant,
    val lastTransitionAt: Instant,
    val dispatchAttempts: Int,
    val claimedAt: Instant?,
    val claimedBy: String?,
    val histories: List<NotificationHistoryDocument>,
)
