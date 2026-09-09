package me.rgunny.kachi.collector.application.service.outbox

import me.rgunny.kachi.collector.application.port.inbound.outbox.FindCollectorOutboxesUseCase
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.CollectorOutboxSummary
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.FindCollectorOutboxesQuery
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.FindCollectorOutboxesResult
import me.rgunny.kachi.collector.application.port.outbound.persistence.CollectorOutboxPersistencePort
import org.springframework.stereotype.Service

/**
 * 상태별 outbox 행을 오래된 순으로 읽는 조회 유스케이스.
 */
@Service
class FindCollectorOutboxesService(
    private val outboxPersistencePort: CollectorOutboxPersistencePort
) : FindCollectorOutboxesUseCase {

    override suspend fun find(query: FindCollectorOutboxesQuery): FindCollectorOutboxesResult {
        val outboxes = outboxPersistencePort.findByStatus(query.status, query.limit)
            .map(CollectorOutboxSummary::from)

        return FindCollectorOutboxesResult(outboxes)
    }
}
