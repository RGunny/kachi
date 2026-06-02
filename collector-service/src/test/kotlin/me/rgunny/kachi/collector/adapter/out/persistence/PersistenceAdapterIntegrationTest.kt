package me.rgunny.kachi.collector.adapter.out.persistence

import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import

@DataMongoTest(
    properties = [
        "spring.data.mongodb.auto-index-creation=true",
        "spring.mongodb.representation.uuid=standard"
    ]
)
@Import(
    NewsPersistenceAdapter::class,
    CollectionRunPersistenceAdapter::class,
    PersistenceAdapterTestContainersConfig::class
)
abstract class PersistenceAdapterIntegrationTest
