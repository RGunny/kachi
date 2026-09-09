package me.rgunny.kachi.notification.routing.adapter.outbound.persistence.job

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import me.rgunny.kachi.notification.routing.adapter.outbound.persistence.job.RoutingJobDocument
import me.rgunny.kachi.notification.routing.adapter.outbound.persistence.job.RoutingJobDocumentMapper
import me.rgunny.kachi.notification.routing.application.port.outbound.job.RoutingJobPersistencePort
import me.rgunny.kachi.notification.routing.domain.RoutingJob
import me.rgunny.kachi.notification.routing.exception.routing.RoutingJobConflictException
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

/**
 * routing job MongoDB 구현.
 *
 * insert는 eventKey unique 위반을 RoutingJobConflictException으로 바꿔 돌려준다.
 */
@Repository
class RoutingJobMongoPersistenceAdapter(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val mapper: RoutingJobDocumentMapper,
) : RoutingJobPersistencePort {

    override suspend fun insert(job: RoutingJob): RoutingJob {
        return try {
            mongoTemplate.insert(mapper.toDocument(job))
                .map(mapper::toDomain)
                .awaitSingle()
        } catch (exception: DuplicateKeyException) {
            throw RoutingJobConflictException(job.eventKey, exception)
        }
    }

    override suspend fun findByEventKey(eventKey: String): RoutingJob? {
        return mongoTemplate.findOne(
            Query.query(Criteria.where(FIELD_EVENT_KEY).`is`(eventKey)),
            RoutingJobDocument::class.java,
        )
            .map(mapper::toDomain)
            .awaitSingleOrNull()
    }

    override suspend fun save(job: RoutingJob): RoutingJob {
        return mongoTemplate.save(mapper.toDocument(job))
            .map(mapper::toDomain)
            .awaitSingle()
    }

    private companion object {
        const val FIELD_EVENT_KEY = "eventKey"
    }
}
