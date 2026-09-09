package me.rgunny.kachi.story.adapter.outbound.persistence

import me.rgunny.kachi.story.StoryServiceTestContainersConfig
import me.rgunny.kachi.story.adapter.outbound.persistence.outbox.StoryOutboxPersistenceAdapter
import me.rgunny.kachi.story.adapter.outbound.persistence.story.StoryArticlePersistenceAdapter
import me.rgunny.kachi.story.adapter.outbound.persistence.story.StoryAssemblyPersistenceAdapter
import me.rgunny.kachi.story.adapter.outbound.persistence.story.StoryPersistenceAdapter
import me.rgunny.kachi.story.config.StoryMongoTransactionConfig
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * MongoDB 영속 adapter 통합 테스트의 공통 컨텍스트.
 */
@ActiveProfiles("test")
@DataMongoTest
@Import(
    StoryPersistenceAdapter::class,
    StoryArticlePersistenceAdapter::class,
    StoryAssemblyPersistenceAdapter::class,
    StoryOutboxPersistenceAdapter::class,
    StoryMongoTransactionConfig::class,
    StoryServiceTestContainersConfig::class
)
abstract class PersistenceAdapterIntegrationTest
