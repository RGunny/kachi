package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.inbound.dlt.model.DiscardNotificationDltMessageCommand
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.DiscardNotificationDltMessageResult
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageDetail
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageAdminResult
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageQuery
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageSummary
import me.rgunny.kachi.notification.application.port.inbound.dlt.NotificationDltMessageAdminUseCase
import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationDltMessageAdminPersistencePort
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.exception.InvalidNotificationDltMessageStateException
import me.rgunny.kachi.notification.exception.NotificationDltMessageNotFoundException
import java.time.Clock
import java.time.Instant

/**
 * DLT 메시지 운영 조회 application service.
 */
class NotificationDltMessageAdminService(
    private val persistencePort: NotificationDltMessageAdminPersistencePort,
    private val clock: Clock,
) : NotificationDltMessageAdminUseCase {

    /**
     * 운영자가 처리 대상을 고를 수 있도록 상태별 DLT 메시지 summary를 조회한다.
     */
    override suspend fun find(query: NotificationDltMessageQuery): NotificationDltMessageAdminResult {
        val messages = persistencePort.findByStatus(query.status, query.batchSize)
            .map(NotificationDltMessageSummary::from)

        return NotificationDltMessageAdminResult(messages)
    }

    /**
     * 운영자가 원본 payload까지 확인할 수 있도록 DLT 메시지 상세를 조회한다.
     */
    override suspend fun get(messageId: NotificationDltMessageId): NotificationDltMessageDetail {
        val message = persistencePort.findById(messageId)
            ?: throw NotificationDltMessageNotFoundException(messageId)

        return NotificationDltMessageDetail.from(message)
    }

    /**
     * 운영자가 재처리하지 않기로 판단한 PENDING DLT 메시지를 DISCARDED로 폐기한다.
     */
    override suspend fun discard(
        command: DiscardNotificationDltMessageCommand
    ): DiscardNotificationDltMessageResult {
        // 1. 운영자가 선택한 DLT 메시지를 조회한다.
        val now = Instant.now(clock)
        val message = persistencePort.findById(command.messageId)
            ?: throw NotificationDltMessageNotFoundException(command.messageId)

        if (!message.canDiscard()) {
            throw InvalidNotificationDltMessageStateException(
                messageId = command.messageId,
                currentStatus = message.status,
                message = "notification dlt message is not discardable. messageId=${command.messageId.id}, currentStatus=${message.status}",
            )
        }

        // 2. domain 전이 규칙으로 PENDING 메시지만 DISCARDED로 닫는다.
        message.discard(now, command.reason)

        // 3. 저장 시점에도 PENDING일 때만 조건부 갱신해 동시 운영 처리 충돌을 막는다.
        val saved = persistencePort.discardIfPending(message)
            ?: throw InvalidNotificationDltMessageStateException(
                messageId = command.messageId,
                currentStatus = null,
                message = "notification dlt message is not discardable. messageId=${command.messageId.id}",
            )

        // 4. 운영 API가 처리 결과를 추적할 수 있도록 폐기 시각과 사유를 반환한다.
        return DiscardNotificationDltMessageResult(
            messageId = saved.id,
            status = saved.status,
            discardedAt = saved.discardedAt ?: now,
            discardReason = saved.discardReason ?: command.reason,
        )
    }
}
