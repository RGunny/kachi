package me.rgunny.kachi.notification.routing.adapter.outbound.persistence.job

import kotlin.test.assertEquals
import me.rgunny.kachi.notification.routing.domain.RoutingJob
import me.rgunny.kachi.notification.routing.domain.RoutingJobKind
import me.rgunny.kachi.notification.routing.domain.RoutingJobStatus
import me.rgunny.kachi.notification.routing.support.RoutingTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RoutingJobDocumentMapper")
class RoutingJobDocumentMapperTest {

    private val mapper = RoutingJobDocumentMapper()

    @Test
    @DisplayName("RoutingJob과 document를 상호 변환할 때 상태와 카운트를 보존한다")
    fun roundTrip() {
        val job = RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", NOW)
            .complete(3, 3, NOW.plusSeconds(5))

        val document = mapper.toDocument(job)
        val restored = mapper.toDomain(document)

        assertEquals(job.id, restored.id)
        assertEquals("summary-1", restored.eventKey)
        assertEquals(RoutingJobKind.SUMMARY, restored.kind)
        assertEquals("tesla", restored.keyword)
        assertEquals(RoutingJobStatus.COMPLETED, restored.status)
        assertEquals(3, restored.targetCount)
        assertEquals(3, restored.publishedCount)
        assertEquals(NOW, restored.createdAt)
        assertEquals(NOW.plusSeconds(5), restored.updatedAt)
        assertEquals(NOW.plusSeconds(5), restored.completedAt)
    }
}
