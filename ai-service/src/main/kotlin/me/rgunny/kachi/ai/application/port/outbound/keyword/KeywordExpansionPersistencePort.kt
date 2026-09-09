package me.rgunny.kachi.ai.application.port.outbound.keyword

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import me.rgunny.kachi.ai.domain.llm.PromptVersion

/**
 * 키워드 확장 결과 저장소 출력 포트
 */
interface KeywordExpansionPersistencePort {

    suspend fun findByUniqueKey(
        keyword: AiKeyword,
        promptVersion: PromptVersion
    ): KeywordExpansion?

    suspend fun save(keywordExpansion: KeywordExpansion): KeywordExpansion

    suspend fun saveOrFindExisting(keywordExpansion: KeywordExpansion): KeywordExpansion
}
