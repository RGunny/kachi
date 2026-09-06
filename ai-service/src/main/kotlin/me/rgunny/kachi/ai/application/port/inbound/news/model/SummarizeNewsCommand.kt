package me.rgunny.kachi.ai.application.port.inbound.news.model

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.NewsSummaryPrompt

/**
 * 뉴스 요약 실행 요청
 */
data class SummarizeNewsCommand(
    val keywords: List<AiKeyword>,
    val window: SummaryWindowRequest,
    val maxArticlesPerKeyword: Int = DEFAULT_MAX_ARTICLES_PER_KEYWORD
) {
    init {
        require(maxArticlesPerKeyword in 1..MAX_ARTICLES_PER_KEYWORD) {
            "키워드별 최대 뉴스 개수는 1 이상 ${MAX_ARTICLES_PER_KEYWORD} 이하여야 합니다"
        }
    }

    companion object {
        const val DEFAULT_MAX_ARTICLES_PER_KEYWORD = 20
        /** 프롬프트가 한 번에 싣는 기사 수와 같다. 여기서 더 받아도 프롬프트 경계에서 잘린다. */
        const val MAX_ARTICLES_PER_KEYWORD = NewsSummaryPrompt.MAX_ARTICLES
    }
}
