package me.rgunny.kachi.collector.adapter.outbound.persistence.collection

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.collector.domain.CollectionRun
import me.rgunny.kachi.collector.domain.CollectionRunStatus
import me.rgunny.kachi.collector.domain.CollectionTargetType
import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.ProviderCollectionResult
import me.rgunny.kachi.collector.domain.ProviderFailureReason
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("CollectionRunPersistenceAdapter 통합 테스트")
class CollectionRunPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: CollectionRunPersistenceAdapter

    @Autowired
    private lateinit var repository: CollectionRunMongoRepository

    private val startedAt = CollectorTestFixture.NOW
    private val finishedAt = startedAt.plusSeconds(60)

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
    }

    @Nested
    @DisplayName("save()")
    inner class Save {

        @Test
        @DisplayName("CollectionRun 도메인을 MongoDB에 저장하고 다시 조회한다")
        fun saveCollectionRunAndFindById() = runBlocking {
            val completedRun = CollectionRun.start(
                targetType = CollectionTargetType.NEWS,
                requestedKeywords = 2,
                startedAt = startedAt
            ).complete(
                providerResults = listOf(
                    ProviderCollectionResult.success(
                        source = NewsSource.GOOGLE,
                        fetchedCount = 3,
                        savedCount = 2,
                        duplicateCount = 1
                    ),
                    ProviderCollectionResult.failure(
                        source = NewsSource.NAVER,
                        failureReason = ProviderFailureReason.TIMEOUT,
                        failureMessage = "timeout"
                    )
                ),
                finishedAt = finishedAt
            )

            val saved = adapter.save(completedRun)
            val found = adapter.findById(saved.id)

            assertNotNull(found)
            assertEquals(saved.id, found.id)
            assertEquals(CollectionRunStatus.PARTIALLY_FAILED, found.status)
            assertEquals(2, found.collectedCount)
            assertEquals(1, found.duplicateCount)
            assertEquals(1, found.failureCount)
            assertEquals("timeout", found.failureReason)
            assertEquals(2, found.providerResults.size)
            assertEquals(ProviderFailureReason.TIMEOUT, found.providerResults[1].failureReason)
        }
    }

    @Nested
    @DisplayName("findById()")
    inner class FindById {

        @Test
        @DisplayName("수집 실행이 없으면 null을 반환한다")
        fun returnNullWhenCollectionRunDoesNotExist() = runBlocking {
            val run = CollectionRun.start(
                targetType = CollectionTargetType.NEWS,
                requestedKeywords = 0,
                startedAt = startedAt
            )

            val found = adapter.findById(run.id)

            assertNull(found)
        }
    }
}
