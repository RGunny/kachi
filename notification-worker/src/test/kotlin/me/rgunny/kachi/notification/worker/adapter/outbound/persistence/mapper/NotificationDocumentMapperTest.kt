package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mapper

import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("worker NotificationDocumentMapper")
class NotificationDocumentMapperTest {

    private val mapper = NotificationDocumentMapper()

    @Test
    @DisplayName("Notification aggregate와 document를 상호 변환할 때 상태와 history를 보존한다")
    fun roundTrip() {
        val requestedAt = Instant.parse("2026-06-17T00:00:00Z")
        val publishedAt = Instant.parse("2026-06-17T00:00:10Z")
        val notification = Notification.request(
            requestId = "request-1",
            requester = "collector-service",
            channel = NotificationChannel.SLACK,
            recipient = "C123",
            message = "hello",
            now = requestedAt,
        ).also {
            it.markPublished(publishedAt)
        }

        val document = mapper.toDocument(notification)
        val restored = mapper.toDomain(document)

        assertEquals(notification.id, restored.id)
        assertEquals("request-1", restored.requestId)
        assertEquals("collector-service", restored.requester)
        assertEquals(NotificationChannel.SLACK, restored.channel)
        assertEquals("C123", restored.recipient)
        assertEquals("hello", restored.message)
        assertEquals(requestedAt, restored.requestedAt)
        assertEquals(NotificationStatus.PUBLISHED, restored.status)
        assertEquals(publishedAt, restored.updatedAt)
        assertEquals(publishedAt, restored.lastTransitionAt)
        assertEquals(1, restored.histories.size)
        assertEquals(NotificationStatus.REQUESTED, restored.histories.first().fromStatus)
        assertEquals(NotificationStatus.PUBLISHED, restored.histories.first().toStatus)
    }

    @Test
    @DisplayName("PROCESSING claim 정보를 document와 domain 사이에서 보존한다")
    fun roundTripProcessingClaim() {
        val requestedAt = Instant.parse("2026-06-17T00:00:00Z")
        val publishedAt = Instant.parse("2026-06-17T00:00:10Z")
        val claimedAt = Instant.parse("2026-06-17T00:00:20Z")
        val notification = Notification.request(
            requestId = "request-2",
            requester = "collector-service",
            channel = NotificationChannel.SLACK,
            recipient = "C123",
            message = "hello",
            now = requestedAt,
        ).also {
            it.markPublished(publishedAt)
            it.markProcessing(claimedAt, "worker-1")
        }

        val document = mapper.toDocument(notification)
        val restored = mapper.toDomain(document)

        assertEquals(claimedAt, document.claimedAt)
        assertEquals("worker-1", document.claimedBy)
        assertEquals(claimedAt, restored.claimedAt)
        assertEquals("worker-1", restored.claimedBy)

        restored.markSent(Instant.parse("2026-06-17T00:00:30Z"))
        assertNull(restored.claimedAt)
        assertNull(restored.claimedBy)
    }
}
