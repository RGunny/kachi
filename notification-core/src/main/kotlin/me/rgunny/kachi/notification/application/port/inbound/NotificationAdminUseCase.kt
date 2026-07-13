package me.rgunny.kachi.notification.application.port.inbound

import me.rgunny.kachi.notification.application.port.dto.admin.RecoverDeadNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.admin.RecoverDeadNotificationResult

/**
 * Notification 현재 상태 document 운영 use case.
 */
interface NotificationAdminUseCase {

    /**
     * 자동 재시도 종료 상태인 DEAD notification을 운영자 판단으로 다시 dispatch 발행 대기 상태로 되돌린다.
     */
    suspend fun recoverDead(command: RecoverDeadNotificationCommand): RecoverDeadNotificationResult
}
