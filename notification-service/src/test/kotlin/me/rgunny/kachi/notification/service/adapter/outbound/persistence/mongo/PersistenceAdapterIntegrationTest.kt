package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationDocumentMapper
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper.NotificationOutboxDocumentMapper
import me.rgunny.kachi.notification.service.config.NotificationMongoTransactionConfig
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import

@DataMongoTest(
    properties = [
        "spring.data.mongodb.auto-index-creation=true",
        "spring.mongodb.representation.uuid=standard"
    ]
)
@Import(
    NotificationDocumentMapper::class,
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
