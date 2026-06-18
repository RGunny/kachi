package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mongo

import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
@DataMongoTest(
    properties = [
        "spring.data.mongodb.auto-index-creation=true",
        "spring.mongodb.representation.uuid=standard",
    ]
)
@Import(
    NotificationDocumentMapper::class,
    NotificationMongoPersistenceAdapter::class,
    PersistenceAdapterTestContainersConfig::class,
)
abstract class PersistenceAdapterIntegrationTest
