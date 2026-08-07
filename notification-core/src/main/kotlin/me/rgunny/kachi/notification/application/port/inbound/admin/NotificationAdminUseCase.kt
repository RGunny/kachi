package me.rgunny.kachi.notification.application.port.inbound.admin

import me.rgunny.kachi.notification.application.port.inbound.admin.model.DeadNotificationQuery
import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationAdminResult
import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationHistoryQuery
import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationHistoryResult
import me.rgunny.kachi.notification.application.port.inbound.admin.model.RecoverDeadNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.admin.model.RecoverDeadNotificationResult

/**
 * Notification 현재 상태 document와 상태 history 운영 use case.
 */
interface NotificationAdminUseCase {

    /**
     * 자동 dispatch 재시도 종료 상태인 DEAD notification 목록을 조회한다.
     */
    suspend fun findDead(query: DeadNotificationQuery): NotificationAdminResult

    /**
     * 특정 notification의 상태 전이 history를 조회한다.
     */
    suspend fun findHistories(query: NotificationHistoryQuery): NotificationHistoryResult

    /**
     * 자동 재시도 종료 상태인 DEAD notification을 운영자 판단으로 다시 dispatch 발행 대기 상태로 되돌린다.
     */
    suspend fun recoverDead(command: RecoverDeadNotificationCommand): RecoverDeadNotificationResult
}
