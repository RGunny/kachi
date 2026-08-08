package me.rgunny.kachi.notification.application.port.outbound.persistence

import me.rgunny.kachi.notification.domain.NotificationDltMessage

/**
 * Notification dispatch DLT 메시지 저장 port.
 */
interface NotificationDltMessagePersistencePort {

    /**
     * 원본 Kafka record 위치 기준으로 DLT 메시지를 idempotent하게 저장한다.
     */
    suspend fun save(message: NotificationDltMessage): NotificationDltMessage
}
