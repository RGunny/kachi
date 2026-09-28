package me.rgunny.kachi.ai.application.service.quarantine

import me.rgunny.kachi.ai.application.port.inbound.quarantine.FindStoryQuarantinesUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindStoryQuarantinesQuery
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindStoryQuarantinesResult
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.StoryQuarantineSnapshot
import me.rgunny.kachi.ai.application.port.outbound.quarantine.StoryQuarantinePersistencePort
import org.springframework.stereotype.Service

/**
 * story 격리 기록을 조건에 맞게 읽는 조회 유스케이스.
 */
@Service
class FindStoryQuarantinesService(
    private val storyQuarantinePersistencePort: StoryQuarantinePersistencePort
) : FindStoryQuarantinesUseCase {

    override suspend fun find(query: FindStoryQuarantinesQuery): FindStoryQuarantinesResult {
        val quarantines = storyQuarantinePersistencePort.findAll(query.status)

        return FindStoryQuarantinesResult(quarantines.map(StoryQuarantineSnapshot::from))
    }
}
