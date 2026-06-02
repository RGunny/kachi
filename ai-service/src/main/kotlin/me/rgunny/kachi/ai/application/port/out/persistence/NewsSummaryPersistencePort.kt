package me.rgunny.kachi.ai.application.port.out.persistence

import me.rgunny.kachi.ai.domain.summary.NewsSummary

/**
 * 뉴스 요약 결과 저장소 출력 포트
 */
interface NewsSummaryPersistencePort {

    suspend fun save(newsSummary: NewsSummary): NewsSummary
}
