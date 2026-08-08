package me.rgunny.kachi.notification.application.port.inbound.dispatch

import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.DispatchNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.DispatchNotificationResult

/**
 * 알림 발송 실행 유스케이스.
 *
 * notification-worker가 Kafka `notification.dispatch` 메시지를 consume한 뒤
 * 실제 외부 채널 발송을 수행할 때 사용한다.
 *
 * 책임:
 * - dispatch 메시지 중복 처리 방어
 * - Notification PROCESSING claim
 * - 채널별 sender 호출
 * - 발송 결과에 따른 SENT/FAILED/RETRY_WAIT/DEAD 상태 전이
 */
interface DispatchNotificationUseCase {

    suspend fun dispatch(command: DispatchNotificationCommand): DispatchNotificationResult
}
