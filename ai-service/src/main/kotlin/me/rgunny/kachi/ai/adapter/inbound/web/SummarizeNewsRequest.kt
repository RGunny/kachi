package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.inbound.news.model.ExplicitSummaryWindowRequest
import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizeNewsCommand
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import java.time.Instant

/**
 * 운영자가 구간을 직접 지정해 실행하는 요청.
 *
 * 지정 구간만 처리하고 watermark는 건드리지 않는다. 임의 구간 실행이 watermark를 움직이면
 * 지정하지 않은 구간까지 처리된 것으로 기록되기 때문이다. 주기 실행은 scheduler가 watermark 기반으로 돌린다.
 */
data class SummarizeNewsRequest(
    val keywords: List<String> = emptyList(),
    val from: Instant? = null,
    val to: Instant? = null,
    val maxArticlesPerKeyword: Int = SummarizeNewsCommand.DEFAULT_MAX_ARTICLES_PER_KEYWORD
) {

    fun toCommand(): SummarizeNewsCommand {
        return SummarizeNewsCommand(
            keywords = keywords.map(AiKeyword::of),
            window = ExplicitSummaryWindowRequest(from = from, to = to),
            maxArticlesPerKeyword = maxArticlesPerKeyword
        )
    }
}
