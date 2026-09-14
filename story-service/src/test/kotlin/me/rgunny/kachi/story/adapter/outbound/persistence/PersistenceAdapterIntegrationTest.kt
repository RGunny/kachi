package me.rgunny.kachi.story.adapter.outbound.persistence

import me.rgunny.kachi.story.StoryServiceTestContainersConfig
import me.rgunny.kachi.story.adapter.outbound.persistence.outbox.StoryOutboxMongoDocument
import me.rgunny.kachi.story.adapter.outbound.persistence.outbox.StoryOutboxPersistenceAdapter
import me.rgunny.kachi.story.adapter.outbound.persistence.story.StoryArticleMongoDocument
import me.rgunny.kachi.story.adapter.outbound.persistence.story.StoryArticlePersistenceAdapter
import me.rgunny.kachi.story.adapter.outbound.persistence.story.StoryAssemblyPersistenceAdapter
import me.rgunny.kachi.story.adapter.outbound.persistence.story.StoryMongoDocument
import me.rgunny.kachi.story.adapter.outbound.persistence.story.StoryPersistenceAdapter
import me.rgunny.kachi.story.adapter.outbound.persistence.story.StoryReorganizePersistenceAdapter
import me.rgunny.kachi.story.config.StoryMongoTransactionConfig
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.test.context.ActiveProfiles

/**
 * MongoDB 영속 adapter 통합 테스트의 공통 컨텍스트.
 *
 * 문서에 선언한 index는 reactive 자동 생성이 비동기라 첫 테스트보다 늦게 만들어질 수 있다.
 * 테스트마다 선언된 index를 직접 보장해 unique 검증이 시점에 따라 흔들리지 않게 한다.
 */
@ActiveProfiles("test")
@DataMongoTest
@Import(
    StoryPersistenceAdapter::class,
    StoryArticlePersistenceAdapter::class,
    StoryAssemblyPersistenceAdapter::class,
    StoryReorganizePersistenceAdapter::class,
    StoryOutboxPersistenceAdapter::class,
    StoryMongoTransactionConfig::class,
    StoryServiceTestContainersConfig::class
)
abstract class PersistenceAdapterIntegrationTest {

    @Autowired
    private lateinit var indexTemplate: ReactiveMongoTemplate

    @Autowired
    private lateinit var mappingContext: MongoMappingContext

    @BeforeEach
    fun ensureDeclaredIndexes() {
        val resolver = MongoPersistentEntityIndexResolver(mappingContext)
        DOCUMENT_TYPES.forEach { type ->
            resolver.resolveIndexFor(type).forEach { index -> indexTemplate.indexOps(type).ensureIndex(index).block() }
        }
    }

    private companion object {
        val DOCUMENT_TYPES: List<Class<*>> = listOf(
            StoryMongoDocument::class.java,
            StoryArticleMongoDocument::class.java,
            StoryOutboxMongoDocument::class.java
        )
    }
}
