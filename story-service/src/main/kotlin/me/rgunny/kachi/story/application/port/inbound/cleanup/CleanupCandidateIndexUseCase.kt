package me.rgunny.kachi.story.application.port.inbound.cleanup

import me.rgunny.kachi.story.application.port.inbound.cleanup.model.CleanupCandidateIndexResult

/**
 * 후보 검색 창을 지난 기사 벡터를 색인에서 지우는 유스케이스.
 */
interface CleanupCandidateIndexUseCase {

    suspend fun cleanup(): CleanupCandidateIndexResult
}
