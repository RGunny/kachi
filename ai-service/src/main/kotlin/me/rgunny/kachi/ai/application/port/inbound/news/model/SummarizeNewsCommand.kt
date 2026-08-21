package me.rgunny.kachi.ai.application.port.inbound.news.model

import me.rgunny.kachi.ai.domain.keyword.AiKeyword

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
        const val MAX_ARTICLES_PER_KEYWORD = 100
    }
}
