package me.rgunny.kachi.collector.adapter.outbound.persistence

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.collector.application.port.outbound.collection.CollectionRunPersistencePort
import me.rgunny.kachi.collector.domain.CollectionRun
import me.rgunny.kachi.collector.domain.CollectionRunId
import org.springframework.stereotype.Component

@Component
class CollectionRunPersistenceAdapter(
    private val repository: CollectionRunMongoRepository
) : CollectionRunPersistencePort {

    override suspend fun findById(id: CollectionRunId): CollectionRun? {
        return repository.findById(id.value)
            .awaitSingleOrNull()
            ?.toDomain()
    }

    override suspend fun save(collectionRun: CollectionRun): CollectionRun {
        return repository.save(CollectionRunMongoDocument.fromDomain(collectionRun))
            .awaitSingle()
            .toDomain()
    }
}
