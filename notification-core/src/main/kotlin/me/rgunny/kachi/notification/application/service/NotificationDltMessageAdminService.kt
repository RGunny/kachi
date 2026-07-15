package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageAdminResult
import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageQuery
import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageSummary
import me.rgunny.kachi.notification.application.port.inbound.NotificationDltMessageAdminUseCase
import me.rgunny.kachi.notification.application.port.outbound.NotificationDltMessageAdminPersistencePort

/**
 * DLT 메시지 운영 조회 application service.
 */
class NotificationDltMessageAdminService(
    private val persistencePort: NotificationDltMessageAdminPersistencePort,
) : NotificationDltMessageAdminUseCase {

    /**
     * 운영자가 처리 대상을 고를 수 있도록 상태별 DLT 메시지 summary를 조회한다.
     */
    override suspend fun find(query: NotificationDltMessageQuery): NotificationDltMessageAdminResult {
        val messages = persistencePort.findByStatus(query.status, query.batchSize)
            .map(NotificationDltMessageSummary::from)

        return NotificationDltMessageAdminResult(messages)
    }
}
