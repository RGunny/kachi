package me.rgunny.kachi.ai.application.port.`in`.news

import me.rgunny.kachi.ai.domain.AiKeyword
import java.time.Instant

/**
 * 뉴스 요약 실행 요청
 */
data class SummarizeNewsCommand(
    val keywords: List<AiKeyword>,
    val from: Instant?,
    val to: Instant?,
    val maxArticlesPerKeyword: Int = DEFAULT_MAX_ARTICLES_PER_KEYWORD
) {
    init {
        require(maxArticlesPerKeyword in 1..MAX_ARTICLES_PER_KEYWORD) {
            "키워드별 최대 뉴스 개수는 1 이상 ${MAX_ARTICLES_PER_KEYWORD} 이하여야 합니다"
        }
        if (from != null && to != null) {
            require(!from.isAfter(to)) { "뉴스 요약 시작 시각은 종료 시각보다 이후일 수 없습니다" }
        }
    }

    companion object {
        const val DEFAULT_MAX_ARTICLES_PER_KEYWORD = 20
        const val MAX_ARTICLES_PER_KEYWORD = 100
    }
}
