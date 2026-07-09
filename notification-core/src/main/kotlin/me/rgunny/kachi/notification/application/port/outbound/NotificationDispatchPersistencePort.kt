package me.rgunny.kachi.notification.application.port.outbound

import me.rgunny.kachi.notification.domain.Notification

/**
 * 외부 채널 발송 결과 저장 port.
 *
 * Slack/Discord/Telegram 같은 vendor API 호출은 DB transaction으로 rollback할 수 없다.
 * 구현체는 vendor 호출 이후의 Notification 최종 상태(SENT/RETRY_WAIT/DEAD)를 저장하는 경계를 책임진다.
 */
interface NotificationDispatchPersistencePort {

    suspend fun saveFinalized(notification: Notification): Notification
}
