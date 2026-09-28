package me.rgunny.kachi.ai.adapter.outbound.llm.openai

import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.domain.summary.StoryDevelopmentKind

/**
 * LLM이 반환한 story 요약 JSON을 그대로 받는 역직렬화 전용 타입.
 *
 * 빠진 필드는 기본값으로 채운다.
 * [sentiment]는 정의에 없는 값이면 [NewsSummarySentiment.UNKNOWN], [developmentKind]는 null을 돌려준다.
 */
data class ParsedStorySummary(
    val title: String = "",
    val content: String = "",
    val sentiment: String = NewsSummarySentiment.UNKNOWN.name,
    val developmentKind: String = ""
) {
    fun sentiment(): NewsSummarySentiment {
        return runCatching { NewsSummarySentiment.valueOf(sentiment.trim().uppercase()) }
            .getOrDefault(NewsSummarySentiment.UNKNOWN)
    }

    fun developmentKind(): StoryDevelopmentKind? {
        return runCatching { StoryDevelopmentKind.valueOf(developmentKind.trim().uppercase()) }
            .getOrNull()
    }
}
