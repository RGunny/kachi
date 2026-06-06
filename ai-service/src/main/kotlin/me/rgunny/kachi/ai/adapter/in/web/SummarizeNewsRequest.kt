package me.rgunny.kachi.ai.adapter.`in`.web

import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsCommand
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import java.time.Instant

data class SummarizeNewsRequest(
    val keywords: List<String> = emptyList(),
    val from: Instant? = null,
    val to: Instant? = null,
    val maxArticlesPerKeyword: Int = SummarizeNewsCommand.DEFAULT_MAX_ARTICLES_PER_KEYWORD
) {

    fun toCommand(): SummarizeNewsCommand {
        return SummarizeNewsCommand(
            keywords = keywords.map(AiKeyword::of),
            from = from,
            to = to,
            maxArticlesPerKeyword = maxArticlesPerKeyword
        )
    }
}
