package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationOutboxDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@DisplayName("NotificationMongoRequestPersistenceAdapter 통합 테스트")
class NotificationMongoRequestPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: NotificationMongoRequestPersistenceAdapter

    @Autowired
    private lateinit var notificationPersistenceAdapter: NotificationMongoPersistenceAdapter

    @Autowired
    private lateinit var outboxPersistenceAdapter: NotificationMongoOutboxPersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val requestedAt = Instant.parse("2026-06-16T00:00:00Z")

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(Query(), NotificationOutboxDocument::class.java).block()
        mongoTemplate.remove(Query(), NotificationDocument::class.java).block()
    }

    @Test
    @DisplayName("Notification과 dispatch outbox를 하나의 transaction으로 저장한다")
    fun saveRequested() = runBlocking {
        val notification = notification("request-1")
        val outbox = outbox(notification.id)

        val saved = adapter.saveRequested(notification, outbox)

        assertEquals(notification.id, saved.id)
        assertNotNull(notificationPersistenceAdapter.findById(notification.id))
        assertNotNull(outboxPersistenceAdapter.findById(outbox.id))
    }

    @Test
    @DisplayName("outbox 저장 실패 시 먼저 저장된 Notification도 rollback한다")
    fun rollbackWhenOutboxSaveFails() = runBlocking {
        val duplicatedNotificationId = NotificationId.newId()
        val existingOutbox = outbox(duplicatedNotificationId)
        outboxPersistenceAdapter.save(existingOutbox)

        val notification = notification("request-rollback")
        val duplicatedOutbox = NotificationOutbox.restore(
            id = existingOutbox.id,
            notificationId = notification.id,
            topic = existingOutbox.topic,
            partitionKey = existingOutbox.partitionKey,
            eventPayload = existingOutbox.eventPayload,
            createdAt = existingOutbox.createdAt,
            outboxStatus = existingOutbox.outboxStatus,
            retryCount = existingOutbox.retryCount,
            nextRetryAt = existingOutbox.nextRetryAt,
            lastError = existingOutbox.lastError,
            publishedAt = existingOutbox.publishedAt,
            claimedAt = existingOutbox.claimedAt,
            claimedBy = existingOutbox.claimedBy,
        )

        assertFailsWith<DuplicateKeyException> {
            adapter.saveRequested(notification, duplicatedOutbox)
        }

        assertEquals(null, notificationPersistenceAdapter.findById(notification.id))
        assertNotNull(outboxPersistenceAdapter.findById(existingOutbox.id))
    }

    private fun notification(requestId: String): Notification {
        return Notification.request(
            requestId = requestId,
            requester = "collector-service",
            channel = NotificationChannel.SLACK,
            recipient = "C123",
            message = "hello",
            now = requestedAt,
        )
    }

    private fun outbox(notificationId: NotificationId): NotificationOutbox {
        return NotificationOutbox.create(
            notificationId = notificationId,
            topic = "notification.dispatch",
            partitionKey = "C123",
            eventPayload = """{"notificationId":"${notificationId.id}"}""",
            now = requestedAt,
        )
    }
}
