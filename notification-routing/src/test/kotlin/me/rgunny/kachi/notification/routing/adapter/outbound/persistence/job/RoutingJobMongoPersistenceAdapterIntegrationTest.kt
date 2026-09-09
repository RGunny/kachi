package me.rgunny.kachi.notification.routing.adapter.outbound.persistence.job

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.routing.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.notification.routing.adapter.outbound.persistence.job.RoutingJobDocument
import me.rgunny.kachi.notification.routing.domain.RoutingJob
import me.rgunny.kachi.notification.routing.domain.RoutingJobKind
import me.rgunny.kachi.notification.routing.domain.RoutingJobStatus
import me.rgunny.kachi.notification.routing.exception.routing.RoutingJobConflictException
import me.rgunny.kachi.notification.routing.support.RoutingTestFixture.NOW
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query

@DisplayName("RoutingJobMongoPersistenceAdapter 통합 테스트")
class RoutingJobMongoPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: RoutingJobMongoPersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(Query(), RoutingJobDocument::class.java).block()
    }

    @Test
    @DisplayName("insert한 job을 eventKey로 찾는다")
    fun insertAndFind() = runBlocking {
        val job = RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", NOW)

        adapter.insert(job)

        val found = adapter.findByEventKey("summary-1")
        assertNotNull(found)
        assertEquals(job.id, found.id)
        assertEquals(RoutingJobStatus.STARTED, found.status)
        assertNull(adapter.findByEventKey("summary-2"))
    }

    @Test
    @DisplayName("같은 eventKey를 다시 insert하면 RoutingJobConflictException이다")
    fun rejectDuplicateEventKey() = runBlocking {
        adapter.insert(RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", NOW))

        val exception = assertFailsWith<RoutingJobConflictException> {
            adapter.insert(RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", NOW.plusSeconds(1)))
        }

        assertEquals("summary-1", exception.eventKey)
        assertEquals(1L, mongoTemplate.count(Query(), RoutingJobDocument::class.java).block())
    }

    @Test
    @DisplayName("complete한 job 저장이 반영된다")
    fun saveCompleted() = runBlocking {
        val job = adapter.insert(RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", NOW))

        adapter.save(job.complete(3, 3, NOW.plusSeconds(5)))

        val found = adapter.findByEventKey("summary-1")
        assertNotNull(found)
        assertEquals(RoutingJobStatus.COMPLETED, found.status)
        assertEquals(3, found.targetCount)
        assertEquals(3, found.publishedCount)
        assertEquals(NOW.plusSeconds(5), found.completedAt)
    }
}
