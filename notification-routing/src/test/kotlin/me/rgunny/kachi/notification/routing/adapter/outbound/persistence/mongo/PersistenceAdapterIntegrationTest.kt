package me.rgunny.kachi.notification.routing.adapter.outbound.persistence.mongo

import me.rgunny.kachi.notification.routing.adapter.outbound.persistence.mapper.RoutingJobDocumentMapper
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
