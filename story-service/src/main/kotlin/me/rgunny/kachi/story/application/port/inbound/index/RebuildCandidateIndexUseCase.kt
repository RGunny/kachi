package me.rgunny.kachi.story.application.port.inbound.index

import me.rgunny.kachi.story.application.port.inbound.index.model.RebuildCandidateIndexResult

/**
 * 저장된 임베딩으로 후보 색인을 다시 만드는 유스케이스.
 */
interface RebuildCandidateIndexUseCase {

    suspend fun rebuild(): RebuildCandidateIndexResult
}
