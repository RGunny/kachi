package me.rgunny.kachi.notification.service.adapter.outbound.persistence.document

import java.time.Instant

/**
 * Notification 상태 전이 이력 document.
 *
 * 초기 구현에서는 notifications document 안에 embedded list로 저장한다.
 * 별도 collection으로 분리하면 aggregate 저장과 history 저장의 원자성을 다시 설계해야 하므로,
 * audit 전용 조회 요구가 생기기 전까지는 embedded 구조를 유지한다.
 */
data class NotificationHistoryDocument(
    val id: String,
    val notificationId: String,
    val fromStatus: String,
    val toStatus: String,
    val reason: String?,
    val createdAt: Instant,
)
