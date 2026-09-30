package me.rgunny.kachi.notification.worker.adapter.outbound.persistence

import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.dlt.NotificationDltMessageDocumentMapper
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.dlt.NotificationMongoDltMessagePersistenceAdapter
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.notification.NotificationDocumentMapper
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.notification.NotificationMongoDispatchPersistenceAdapter
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.notification.NotificationMongoPersistenceAdapter
import me.rgunny.kachi.notification.worker.config.NotificationWorkerMongoTransactionConfig
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@DataMongoTest
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
