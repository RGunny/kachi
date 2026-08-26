package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.inbound.keyword.model.ExpandKeywordsCommand
import me.rgunny.kachi.ai.domain.keyword.AiKeyword

data class ExpandKeywordsRequest(
    val keywords: List<String> = emptyList(),
    val maxExpansionsPerKeyword: Int = ExpandKeywordsCommand.DEFAULT_MAX_EXPANSIONS_PER_KEYWORD
) {

    fun toCommand(): ExpandKeywordsCommand {
        return ExpandKeywordsCommand(
            keywords = keywords.map(AiKeyword::of),
            maxExpansionsPerKeyword = maxExpansionsPerKeyword
        )
    }
}
