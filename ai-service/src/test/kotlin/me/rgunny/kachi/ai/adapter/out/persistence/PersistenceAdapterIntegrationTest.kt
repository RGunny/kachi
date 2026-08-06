package me.rgunny.kachi.ai.adapter.out.persistence

import me.rgunny.kachi.ai.AiServiceTestContainersConfig
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@DataMongoTest
@Import(
    AiRunPersistenceAdapter::class,
    KeywordExpansionPersistenceAdapter::class,
    NewsSummaryPersistenceAdapter::class,
    SummaryWatermarkPersistenceAdapter::class,
    KeywordQuarantinePersistenceAdapter::class,
    AiServiceTestContainersConfig::class
)
abstract class PersistenceAdapterIntegrationTest
