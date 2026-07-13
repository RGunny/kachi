package me.rgunny.kachi.notification.domain

/**
 * DLT 메시지 운영 처리 상태.
 */
enum class NotificationDltMessageStatus {
    PENDING,
    REPROCESSED,
    DISCARDED,
}
