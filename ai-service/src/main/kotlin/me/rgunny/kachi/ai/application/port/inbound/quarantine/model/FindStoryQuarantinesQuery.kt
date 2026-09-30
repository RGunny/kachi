package me.rgunny.kachi.ai.application.port.inbound.quarantine.model

import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantineStatus

/**
 * story 격리 기록 조회 조건.
 *
 * null인 조건은 걸지 않는다.
 */
data class FindStoryQuarantinesQuery(
    val status: StoryQuarantineStatus?
)
