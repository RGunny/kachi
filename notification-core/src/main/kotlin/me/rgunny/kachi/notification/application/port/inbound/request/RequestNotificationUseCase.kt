package me.rgunny.kachi.notification.application.port.inbound.request

import me.rgunny.kachi.notification.application.port.inbound.request.model.RequestNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.request.model.RequestNotificationResult

/**
 * 외부 알림 요청 접수 유스케이스.
 *
 * 요청을 adapter에서 변환해 application layer로 전달하는 입력 모델이다.
 * - HTTP request body
 * - Kafka notification.requested event
 *
 * 책임:
 * - 요청 멱등성 확인
 * - Notification을 REQUESTED 상태로 저장
 * - worker dispatch 발행을 위한 Outbox를 PENDING 상태로 저장
 */
interface RequestNotificationUseCase {

    suspend fun request(command: RequestNotificationCommand): RequestNotificationResult
}
