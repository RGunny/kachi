package me.rgunny.kachi.ai.application.port.inbound.quarantine.model

data class FindKeywordQuarantinesResult(
    val quarantines: List<KeywordQuarantineSummary>
)
