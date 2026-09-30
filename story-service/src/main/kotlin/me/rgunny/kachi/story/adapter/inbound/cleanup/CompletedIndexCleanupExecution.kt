package me.rgunny.kachi.story.adapter.inbound.cleanup

import me.rgunny.kachi.story.application.port.inbound.cleanup.model.CleanupCandidateIndexResult

/**
 * lock을 획득해 색인 정리가 실행된 결과.
 */
data class CompletedIndexCleanupExecution(
    val result: CleanupCandidateIndexResult
) : IndexCleanupExecution
