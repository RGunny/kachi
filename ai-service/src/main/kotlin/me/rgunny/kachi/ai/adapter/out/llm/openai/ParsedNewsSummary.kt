package me.rgunny.kachi.ai.adapter.out.llm.openai

import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment

/**
 * LLM이 반환한 뉴스 요약 JSON을 그대로 받는 역직렬화 전용 타입.
 *
 * provider가 필드를 빠뜨리거나 정의에 없는 sentiment를 보내도 파싱 자체는 실패하지 않도록
 * 기본값을 두고, 값 검증은 [OpenAiLlmProvider]가 맡는다.
 */
internal data class ParsedNewsSummary(
    val title: String = "",
    val content: String = "",
    val sentiment: String = NewsSummarySentiment.UNKNOWN.name
) {
    fun sentiment(): NewsSummarySentiment {
        return runCatching { NewsSummarySentiment.valueOf(sentiment.trim().uppercase()) }
            .getOrDefault(NewsSummarySentiment.UNKNOWN)
    }
}
