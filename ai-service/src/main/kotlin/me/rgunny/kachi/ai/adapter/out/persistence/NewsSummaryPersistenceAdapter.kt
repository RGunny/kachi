package me.rgunny.kachi.ai.adapter.out.persistence

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.ai.application.port.out.persistence.NewsSummaryPersistencePort
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import org.springframework.stereotype.Component

@Component
class NewsSummaryPersistenceAdapter(
    private val repository: NewsSummaryMongoRepository
) : NewsSummaryPersistencePort {

    override suspend fun save(newsSummary: NewsSummary): NewsSummary {
        return repository.save(NewsSummaryMongoDocument.fromDomain(newsSummary))
            .awaitSingle()
            .toDomain()
    }
}
