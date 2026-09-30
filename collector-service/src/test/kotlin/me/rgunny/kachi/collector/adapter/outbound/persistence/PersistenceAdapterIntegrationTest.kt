package me.rgunny.kachi.collector.adapter.outbound.persistence

import me.rgunny.kachi.collector.CollectorServiceTestContainersConfig
import me.rgunny.kachi.collector.adapter.outbound.persistence.collection.CollectionRunPersistenceAdapter
import me.rgunny.kachi.collector.adapter.outbound.persistence.news.NewsPersistenceAdapter
import me.rgunny.kachi.collector.adapter.outbound.persistence.outbox.CollectorOutboxPersistenceAdapter
import me.rgunny.kachi.collector.config.CollectorMongoTransactionConfig
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@DataMongoTest
@Import(
    NewsPersistenceAdapter::class,
    CollectionRunPersistenceAdapter::class,
    CollectorOutboxPersistenceAdapter::class,
    CollectorMongoTransactionConfig::class,
    CollectorServiceTestContainersConfig::class
)
abstract class PersistenceAdapterIntegrationTest
