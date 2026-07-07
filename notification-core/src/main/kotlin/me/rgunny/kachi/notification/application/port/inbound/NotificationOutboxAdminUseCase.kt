package me.rgunny.kachi.notification.application.port.inbound

import me.rgunny.kachi.notification.application.port.dto.outbox.DeadNotificationOutboxQuery
import me.rgunny.kachi.notification.application.port.dto.outbox.NotificationOutboxAdminResult
import me.rgunny.kachi.notification.application.port.dto.outbox.RecoverNotificationOutboxCommand
import me.rgunny.kachi.notification.application.port.dto.outbox.RecoverNotificationOutboxResult

/**
 * notification.dispatch 발행 outbox 운영 use case.
 *
 * 자동 발행 재시도 한도를 초과한 DEAD outbox를 운영자가 확인하고,
 * 필요할 때 PENDING으로 되돌려 기존 outbox scheduler가 다시 발행하도록 한다.
 */
interface NotificationOutboxAdminUseCase {

    suspend fun findDead(query: DeadNotificationOutboxQuery): NotificationOutboxAdminResult

    suspend fun recover(command: RecoverNotificationOutboxCommand): RecoverNotificationOutboxResult
}
