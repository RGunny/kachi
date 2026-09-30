package me.rgunny.kachi.notification.routing.adapter.outbound.persistence

import me.rgunny.kachi.notification.routing.adapter.outbound.persistence.job.RoutingJobDocumentMapper
import me.rgunny.kachi.notification.routing.adapter.outbound.persistence.job.RoutingJobMongoPersistenceAdapter
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@DataMongoTest
@Import(
    RoutingJobDocumentMapper::class,
    RoutingJobMongoPersistenceAdapter::class,
    PersistenceAdapterTestContainersConfig::class,
)
abstract class PersistenceAdapterIntegrationTest
