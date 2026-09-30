package me.rgunny.kachi.story.application.service.outbox

import me.rgunny.kachi.story.application.port.inbound.outbox.FindStoryOutboxesUseCase
import me.rgunny.kachi.story.application.port.inbound.outbox.model.FindStoryOutboxesQuery
import me.rgunny.kachi.story.application.port.inbound.outbox.model.FindStoryOutboxesResult
import me.rgunny.kachi.story.application.port.inbound.outbox.model.StoryOutboxSummary
import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxPersistencePort
import org.springframework.stereotype.Service

/**
 * 상태별 outbox 행을 오래된 순으로 읽는 조회 유스케이스.
 */
@Service
class FindStoryOutboxesService(
    private val outboxPersistencePort: StoryOutboxPersistencePort
) : FindStoryOutboxesUseCase {

    override suspend fun find(query: FindStoryOutboxesQuery): FindStoryOutboxesResult {
        val outboxes = outboxPersistencePort.findByStatus(query.status, query.limit)
            .map(StoryOutboxSummary::from)

        return FindStoryOutboxesResult(outboxes)
    }
}
