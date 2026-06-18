package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.document

import java.time.Instant

/**
 * notifications document에 embedded로 저장되는 상태 전이 이력.
 */
data class NotificationHistoryDocument(
    val id: String,
    val notificationId: String,
    val fromStatus: String,
    val toStatus: String,
    val reason: String?,
    val createdAt: Instant,
)
