package me.rgunny.kachi.ai.adapter.out.persistence

import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import

@DataMongoTest(
    properties = [
        "spring.data.mongodb.auto-index-creation=true",
        "spring.mongodb.representation.uuid=standard"
    ]
)
@Import(
    AiRunPersistenceAdapter::class,
    KeywordExpansionPersistenceAdapter::class,
    NewsSummaryPersistenceAdapter::class,
    PersistenceAdapterTestContainersConfig::class
)
abstract class PersistenceAdapterIntegrationTest
