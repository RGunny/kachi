package me.rgunny.kachi.ai.application.service.outbox

import me.rgunny.kachi.ai.application.port.inbound.outbox.FindAiOutboxesUseCase
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.AiOutboxSummary
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.FindAiOutboxesQuery
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.FindAiOutboxesResult
import me.rgunny.kachi.ai.application.port.outbound.persistence.AiOutboxPersistencePort
import org.springframework.stereotype.Service

/**
 * 상태별 outbox 행을 오래된 순으로 읽는다.
 */
@Service
class FindAiOutboxesService(
    private val outboxPersistencePort: AiOutboxPersistencePort
) : FindAiOutboxesUseCase {

    override suspend fun find(query: FindAiOutboxesQuery): FindAiOutboxesResult {
        val outboxes = outboxPersistencePort.findByStatus(query.status, query.limit)
            .map(AiOutboxSummary::from)

        return FindAiOutboxesResult(outboxes)
    }
}
