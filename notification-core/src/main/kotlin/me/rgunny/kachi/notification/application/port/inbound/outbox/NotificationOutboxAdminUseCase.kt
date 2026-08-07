package me.rgunny.kachi.notification.application.port.inbound.outbox

import me.rgunny.kachi.notification.application.port.inbound.outbox.model.DeadNotificationOutboxQuery
import me.rgunny.kachi.notification.application.port.inbound.outbox.model.NotificationOutboxAdminResult
import me.rgunny.kachi.notification.application.port.inbound.outbox.model.RecoverNotificationOutboxCommand
import me.rgunny.kachi.notification.application.port.inbound.outbox.model.RecoverNotificationOutboxResult

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
