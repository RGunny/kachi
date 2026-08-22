package me.rgunny.kachi.ai.application.port.outbound.persistence

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.summary.NewsSummary

/**
 * 뉴스 요약 결과 저장소 출력 포트
 */
interface NewsSummaryPersistencePort {

    suspend fun findByUniqueKey(
        keyword: AiKeyword,
        newsHash: String,
        promptVersion: PromptVersion
    ): NewsSummary?

    suspend fun save(newsSummary: NewsSummary): NewsSummary

    suspend fun saveOrFindExisting(newsSummary: NewsSummary): NewsSummary
}
