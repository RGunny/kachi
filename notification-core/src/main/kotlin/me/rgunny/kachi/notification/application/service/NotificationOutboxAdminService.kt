package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.inbound.outbox.model.DeadNotificationOutboxQuery
import me.rgunny.kachi.notification.application.port.inbound.outbox.model.NotificationOutboxAdminResult
import me.rgunny.kachi.notification.application.port.inbound.outbox.model.NotificationOutboxSummary
import me.rgunny.kachi.notification.application.port.inbound.outbox.model.RecoverNotificationOutboxCommand
import me.rgunny.kachi.notification.application.port.inbound.outbox.model.RecoverNotificationOutboxResult
import me.rgunny.kachi.notification.application.port.inbound.outbox.NotificationOutboxAdminUseCase
import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationOutboxPersistencePort
import me.rgunny.kachi.notification.exception.NotificationOutboxNotFoundException
import java.time.Clock
import java.time.Instant

/**
 * Outbox DEAD 운영 복구 application service.
 *
 * 복구는 Kafka publish를 직접 수행하지 않고 DEAD -> PENDING 상태 전이만 수행한다.
 * 실제 발행은 기존 PublishNotificationDispatchUseCase/scheduler가 담당해야
 * claim, retry, stale publishing 회수 규칙을 동일하게 적용할 수 있다.
 */
class NotificationOutboxAdminService(
    private val outboxPersistencePort: NotificationOutboxPersistencePort,
    private val clock: Clock,
) : NotificationOutboxAdminUseCase {

    override suspend fun findDead(query: DeadNotificationOutboxQuery): NotificationOutboxAdminResult {
        val outboxes = outboxPersistencePort.findDead(query.batchSize)
            .map(NotificationOutboxSummary::from)

        return NotificationOutboxAdminResult(outboxes)
    }

    override suspend fun recover(command: RecoverNotificationOutboxCommand): RecoverNotificationOutboxResult {
        // 1. 운영자가 선택한 outbox를 조회한다.
        val now = Instant.now(clock)
        val outbox = outboxPersistencePort.findById(command.outboxId)
            ?: throw NotificationOutboxNotFoundException(command.outboxId)

        // 2. domain 전이 규칙으로 DEAD outbox만 PENDING으로 되돌린다.
        outbox.recoverToPending(now)

        // 3. 다음 scheduler tick에서 기존 outbox 발행 흐름을 다시 타도록 저장한다.
        val saved = outboxPersistencePort.save(outbox)
        return RecoverNotificationOutboxResult(
            outbox = NotificationOutboxSummary.from(saved),
            recoveredAt = now,
        )
    }
}
