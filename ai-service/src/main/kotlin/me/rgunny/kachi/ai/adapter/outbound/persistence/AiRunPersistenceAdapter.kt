package me.rgunny.kachi.ai.adapter.outbound.persistence

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.ai.application.port.outbound.persistence.AiRunPersistencePort
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunId
import org.springframework.stereotype.Component

@Component
class AiRunPersistenceAdapter(
    private val repository: AiRunMongoRepository
) : AiRunPersistencePort {

    override suspend fun findById(id: AiRunId): AiRun? {
        return repository.findById(id.value)
            .awaitSingleOrNull()
            ?.toDomain()
    }

    override suspend fun save(aiRun: AiRun): AiRun {
        return repository.save(AiRunMongoDocument.fromDomain(aiRun))
            .awaitSingle()
            .toDomain()
    }
}
