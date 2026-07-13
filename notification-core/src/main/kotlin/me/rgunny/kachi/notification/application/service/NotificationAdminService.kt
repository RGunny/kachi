package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.dto.NotificationDispatchMessage
import me.rgunny.kachi.notification.application.port.dto.admin.DeadNotificationQuery
import me.rgunny.kachi.notification.application.port.dto.admin.NotificationAdminResult
import me.rgunny.kachi.notification.application.port.dto.admin.NotificationHistoryQuery
import me.rgunny.kachi.notification.application.port.dto.admin.NotificationHistoryResult
import me.rgunny.kachi.notification.application.port.dto.admin.NotificationHistorySummary
import me.rgunny.kachi.notification.application.port.dto.admin.NotificationSummary
import me.rgunny.kachi.notification.application.port.dto.admin.RecoverDeadNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.admin.RecoverDeadNotificationResult
import me.rgunny.kachi.notification.application.port.inbound.NotificationAdminUseCase
import me.rgunny.kachi.notification.application.port.outbound.NotificationAdminPersistencePort
import me.rgunny.kachi.notification.application.port.outbound.NotificationEventSerializer
import me.rgunny.kachi.notification.application.port.outbound.NotificationPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.exception.InvalidNotificationStateException
import me.rgunny.kachi.notification.exception.NotificationNotFoundException
import java.time.Clock
import java.time.Instant

/**
 * Notification 현재 상태 document 운영 기능을 담당하는 application service.
 *
 * DEAD notification 복구는 Kafka를 직접 발행하지 않고 새 outbox를 만들어 기존 publish scheduler 흐름을 재사용한다.
 */
class NotificationAdminService(
    private val notificationPersistencePort: NotificationPersistencePort,
    private val adminPersistencePort: NotificationAdminPersistencePort,
    private val eventSerializer: NotificationEventSerializer,
    private val policy: RequestNotificationPolicy,
    private val clock: Clock,
) : NotificationAdminUseCase {

    override suspend fun findDead(query: DeadNotificationQuery): NotificationAdminResult {
        val notifications = adminPersistencePort.findDead(query.batchSize)
            .map(NotificationSummary::from)

        return NotificationAdminResult(notifications)
    }

    override suspend fun findHistories(query: NotificationHistoryQuery): NotificationHistoryResult {
        val histories = adminPersistencePort.findHistories(query.notificationId, query.batchSize)
            .map(NotificationHistorySummary::from)

        return NotificationHistoryResult(histories)
    }

    /**
     * DEAD notification을 REQUESTED로 되돌리고 새 dispatch outbox를 생성한다.
     */
    override suspend fun recoverDead(command: RecoverDeadNotificationCommand): RecoverDeadNotificationResult {
        // 1. 운영자가 선택한 notification 현재 상태를 조회한다.
        val now = Instant.now(clock)
        val notification = notificationPersistencePort.findById(command.notificationId)
            ?: throw NotificationNotFoundException(command.notificationId)

        // 2. domain 전이 규칙으로 DEAD만 REQUESTED로 되돌린다.
        notification.recoverDeadToRequested(now, command.reason)

        // 3. 기존 outbox publish 흐름을 재사용할 새 dispatch outbox를 만든다.
        val outbox = NotificationOutbox.create(
            notificationId = notification.id,
            topic = policy.dispatchTopic,
            partitionKey = notification.recipient,
            eventPayload = eventSerializer.serializeDispatch(notification.toDispatchMessage()),
            now = now,
        )

        // 4. Notification 상태, 상태 전이 history, 새 outbox를 같은 저장 경계에서 확정한다.
        val saved = adminPersistencePort.recoverDeadToRequested(notification, outbox)
            ?: throw InvalidNotificationStateException(
                notificationId = notification.id,
                currentStatus = null,
                message = "notification is not recoverable from DEAD. notificationId=${notification.id.id}",
            )

        // 5. 운영 API가 추적할 수 있도록 복구된 notification과 새 outbox 식별자를 반환한다.
        return RecoverDeadNotificationResult(
            notificationId = saved.id,
            status = saved.status,
            outboxId = outbox.id,
            recoveredAt = now,
        )
    }

    /**
     * 복구 후 outbox에 저장할 worker dispatch payload를 만든다.
     */
    private fun Notification.toDispatchMessage(): NotificationDispatchMessage {
        return NotificationDispatchMessage(
            notificationId = id,
            requestId = requestId,
            channel = channel,
            recipient = recipient,
            message = message.orEmpty(),
        )
    }
}
