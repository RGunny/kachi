package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.keyword.AiKeyword

/**
 * 선조회 계획([plan])과 LLM 호출 대상을 일치시키는 story 요약 실행 단위.
 */
interface PreparedLlmStorySummary {
    val plan: LlmStorySummaryPlan

    suspend fun summarize(
        keywords: List<AiKeyword>,
        previousSummary: PreviousStorySummary?,
        articles: List<StorySummaryArticle>
    ): LlmStorySummaryResult
}
