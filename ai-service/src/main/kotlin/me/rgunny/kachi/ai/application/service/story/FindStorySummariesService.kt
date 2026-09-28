package me.rgunny.kachi.ai.application.service.story

import me.rgunny.kachi.ai.application.port.inbound.story.FindStorySummariesUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.model.FindStorySummariesQuery
import me.rgunny.kachi.ai.application.port.inbound.story.model.FindStorySummariesResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.StorySummarySnapshot
import me.rgunny.kachi.ai.application.port.outbound.summary.StorySummaryPersistencePort
import org.springframework.stereotype.Service

/**
 * story의 요약 버전들을 최신 순으로 읽는 조회 유스케이스.
 */
@Service
class FindStorySummariesService(
    private val storySummaryPersistencePort: StorySummaryPersistencePort
) : FindStorySummariesUseCase {

    override suspend fun find(query: FindStorySummariesQuery): FindStorySummariesResult {
        val summaries = storySummaryPersistencePort.findByStory(storyId = query.storyId, limit = query.limit)

        return FindStorySummariesResult(summaries.map(StorySummarySnapshot::from))
    }
}
