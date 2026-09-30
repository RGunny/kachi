package me.rgunny.kachi.ai.application.service.news

import me.rgunny.kachi.ai.domain.run.AiSkipReason

data class SkippedKeywordOutcome(
    val reason: AiSkipReason
) : KeywordOutcome
