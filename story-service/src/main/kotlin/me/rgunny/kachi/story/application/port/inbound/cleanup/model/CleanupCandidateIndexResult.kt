package me.rgunny.kachi.story.application.port.inbound.cleanup.model

import java.time.Instant

/**
 * 색인 정리 한 번의 결과.
 *
 * [threshold] 이전에 수집된 기사 벡터가 지워졌다.
 */
data class CleanupCandidateIndexResult(
    val threshold: Instant
)
