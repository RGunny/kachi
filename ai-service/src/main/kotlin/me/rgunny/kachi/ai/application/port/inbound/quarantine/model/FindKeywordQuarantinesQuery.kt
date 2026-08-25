package me.rgunny.kachi.ai.application.port.inbound.quarantine.model

import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiRunTargetType

/**
 * 격리 기록 조회 조건. 두 필드 모두 null이면 전체를 읽는다.
 */
data class FindKeywordQuarantinesQuery(
    val targetType: AiRunTargetType?,
    val status: KeywordQuarantineStatus?
)
