package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.dto.dlt.PersistNotificationDltMessageCommand
import me.rgunny.kachi.notification.application.port.dto.dlt.PersistNotificationDltMessageResult
import me.rgunny.kachi.notification.application.port.inbound.PersistNotificationDltMessageUseCase
import me.rgunny.kachi.notification.application.port.outbound.NotificationDltMessagePersistencePort
import me.rgunny.kachi.notification.domain.NotificationDltMessage

/**
 * Kafka DLT에 도달한 notification dispatch 메시지를 운영 저장소에 보관한다.
 */
class PersistNotificationDltMessageService(
    private val persistencePort: NotificationDltMessagePersistencePort,
) : PersistNotificationDltMessageUseCase {

    override suspend fun persist(
        command: PersistNotificationDltMessageCommand
    ): PersistNotificationDltMessageResult {
        val saved = persistencePort.save(
            NotificationDltMessage.record(
                originalTopic = command.originalTopic,
                originalPartition = command.originalPartition,
                originalOffset = command.originalOffset,
                originalTimestamp = command.originalTimestamp,
                dltTopic = command.dltTopic,
                dltPartition = command.dltPartition,
                dltOffset = command.dltOffset,
                consumerGroup = command.consumerGroup,
                messageKey = command.messageKey,
                payload = command.payload,
                exceptionFqcn = command.exceptionFqcn,
                exceptionMessage = command.exceptionMessage,
                deadLetteredAt = command.deadLetteredAt,
                storedAt = command.storedAt,
            )
        )

        return PersistNotificationDltMessageResult(
            messageId = saved.id,
            status = saved.status,
        )
    }
}
