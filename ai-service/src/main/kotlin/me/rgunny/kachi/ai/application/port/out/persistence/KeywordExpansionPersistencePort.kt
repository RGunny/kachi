package me.rgunny.kachi.ai.application.port.out.persistence

import me.rgunny.kachi.ai.domain.KeywordExpansion

/**
 * 키워드 확장 결과 저장소 출력 포트
 */
interface KeywordExpansionPersistencePort {

    suspend fun save(keywordExpansion: KeywordExpansion): KeywordExpansion
}
