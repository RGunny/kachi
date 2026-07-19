package me.rgunny.kachi.collector.adapter.out.persistence

import me.rgunny.kachi.collector.CollectorServiceTestContainersConfig
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@DataMongoTest
@Import(
    NewsPersistenceAdapter::class,
    CollectionRunPersistenceAdapter::class,
    CollectorServiceTestContainersConfig::class
)
abstract class PersistenceAdapterIntegrationTest
