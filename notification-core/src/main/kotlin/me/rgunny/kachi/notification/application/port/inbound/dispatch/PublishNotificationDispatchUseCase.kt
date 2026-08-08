package me.rgunny.kachi.notification.application.port.inbound.dispatch

import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.PublishNotificationDispatchResult

/**
 * 알림 dispatch 이벤트 발행 유스케이스.
 *
 * notification-service의 outbox publisher 또는 recovery scheduler가
 * PENDING outbox를 `notification.dispatch` topic으로 발행할 때 사용한다.
 *
 * 책임:
 * - 발행 가능한 PENDING outbox 조회
 * - `notification.dispatch` 이벤트 발행
 * - 발행 성공 시 Notification/Outbox 상태 갱신
 * - 발행 실패 시 retry 정보 기록
 */
interface PublishNotificationDispatchUseCase {

    suspend fun publishPending(): PublishNotificationDispatchResult
}
