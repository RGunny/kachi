package me.rgunny.kachi.notification.service.adapter.outbound.persistence

import me.rgunny.kachi.notification.service.adapter.outbound.persistence.dlt.NotificationDltMessageDocumentMapper
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.dlt.NotificationMongoDltMessageAdminPersistenceAdapter
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.notification.NotificationDocumentMapper
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.notification.NotificationMongoAdminPersistenceAdapter
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.notification.NotificationMongoPersistenceAdapter
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.notification.NotificationMongoPublishPersistenceAdapter
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.notification.NotificationMongoRequestPersistenceAdapter
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.outbox.NotificationMongoOutboxPersistenceAdapter
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.outbox.NotificationOutboxDocumentMapper
import me.rgunny.kachi.notification.service.config.NotificationMongoTransactionConfig
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@DataMongoTest
@Import(
    NotificationDltMessageDocumentMapper::class,
    NotificationDocumentMapper::class,
    NotificationMongoDltMessageAdminPersistenceAdapter::class,
    NotificationOutboxDocumentMapper::class,
    NotificationMongoPersistenceAdapter::class,
    NotificationMongoAdminPersistenceAdapter::class,
    NotificationMongoOutboxPersistenceAdapter::class,
    NotificationMongoPublishPersistenceAdapter::class,
    NotificationMongoRequestPersistenceAdapter::class,
    NotificationMongoTransactionConfig::class,
    PersistenceAdapterTestContainersConfig::class,
)
abstract class PersistenceAdapterIntegrationTest
