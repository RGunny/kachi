package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mapper

import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

@DisplayName("NotificationOutboxDocumentMapper")
class NotificationOutboxDocumentMapperTest {

    private val mapper = NotificationOutboxDocumentMapper()

    @Test
    @DisplayName("NotificationOutbox aggregate와 document를 상호 변환할 때 claim 상태를 보존한다")
    fun roundTrip() {
        val createdAt = Instant.parse("2026-06-16T00:00:00Z")
        val claimedAt = Instant.parse("2026-06-16T00:00:10Z")
        val outbox = NotificationOutbox.create(
            notificationId = NotificationId.newId(),
            topic = "notification.dispatch",
            partitionKey = "user-1",
            eventPayload = """{"notificationId":"n1"}""",
            now = createdAt,
        ).markPublishing(claimedAt, "notification-service-1")

        val document = mapper.toDocument(outbox)
        val restored = mapper.toDomain(document)

        assertEquals(outbox.id, restored.id)
        assertEquals(outbox.notificationId, restored.notificationId)
        assertEquals("notification.dispatch", restored.topic)
        assertEquals("user-1", restored.partitionKey)
        assertEquals("""{"notificationId":"n1"}""", restored.eventPayload)
        assertEquals(createdAt, restored.createdAt)
        assertEquals(NotificationOutboxStatus.PUBLISHING, restored.outboxStatus)
        assertEquals(0, restored.retryCount)
        assertEquals(createdAt, restored.nextRetryAt)
        assertEquals(claimedAt, restored.claimedAt)
        assertEquals("notification-service-1", restored.claimedBy)
    }
}
