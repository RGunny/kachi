package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationDltMessageDocumentMapper
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationOutboxDocumentMapper
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
