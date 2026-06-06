package me.rgunny.kachi.ai.application.port.`in`.keyword

import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * 키워드 확장 실행 요청
 */
data class ExpandKeywordsCommand(
    val keywords: List<AiKeyword>,
    val maxExpansionsPerKeyword: Int = DEFAULT_MAX_EXPANSIONS_PER_KEYWORD
) {
    init {
        require(maxExpansionsPerKeyword in 1..MAX_EXPANSIONS_PER_KEYWORD) {
            "키워드별 최대 확장 개수는 1 이상 ${MAX_EXPANSIONS_PER_KEYWORD} 이하여야 합니다"
        }
    }

    companion object {
        const val DEFAULT_MAX_EXPANSIONS_PER_KEYWORD = 5
        const val MAX_EXPANSIONS_PER_KEYWORD = 20
    }
}
