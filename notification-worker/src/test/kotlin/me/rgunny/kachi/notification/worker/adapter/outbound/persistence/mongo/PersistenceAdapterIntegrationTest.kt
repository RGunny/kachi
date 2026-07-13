package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mongo

import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mapper.NotificationDltMessageDocumentMapper
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import me.rgunny.kachi.notification.worker.config.NotificationWorkerMongoTransactionConfig
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
    NotificationDltMessageDocumentMapper::class,
    NotificationDocumentMapper::class,
    NotificationMongoDltMessagePersistenceAdapter::class,
    NotificationMongoDispatchPersistenceAdapter::class,
    NotificationMongoPersistenceAdapter::class,
    NotificationWorkerMongoTransactionConfig::class,
    PersistenceAdapterTestContainersConfig::class,
)
abstract class PersistenceAdapterIntegrationTest
