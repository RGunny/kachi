package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.run.AiRunPersistencePort
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunId

class FakeAiRunPersistencePort : AiRunPersistencePort {
    val savedRuns: MutableList<AiRun> = mutableListOf()

    override suspend fun findById(id: AiRunId): AiRun? {
        return savedRuns.firstOrNull { it.id == id }
    }

    override suspend fun save(aiRun: AiRun): AiRun {
        savedRuns.add(aiRun)
        return aiRun
    }
}
