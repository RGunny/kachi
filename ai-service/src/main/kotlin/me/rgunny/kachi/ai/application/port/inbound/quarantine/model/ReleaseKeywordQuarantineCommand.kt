package me.rgunny.kachi.ai.application.port.inbound.quarantine.model

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.run.AiRunTargetType

data class ReleaseKeywordQuarantineCommand(
    val targetType: AiRunTargetType,
    val keyword: AiKeyword
)
