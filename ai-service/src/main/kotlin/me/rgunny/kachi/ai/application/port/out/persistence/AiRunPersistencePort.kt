package me.rgunny.kachi.ai.application.port.out.persistence

import me.rgunny.kachi.ai.domain.AiRun
import me.rgunny.kachi.ai.domain.AiRunId

/**
 * AI 실행 기록 저장소 출력 포트
 */
interface AiRunPersistencePort {

    suspend fun findById(id: AiRunId): AiRun?

    suspend fun save(aiRun: AiRun): AiRun
}
