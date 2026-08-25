package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxStatus
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.retry.RetryPolicy
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationOutboxDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@DisplayName("NotificationMongoPublishPersistenceAdapter 통합 테스트")
class NotificationMongoPublishPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: NotificationMongoPublishPersistenceAdapter

    @Autowired
    private lateinit var notificationPersistenceAdapter: NotificationMongoPersistenceAdapter

    @Autowired
    private lateinit var outboxPersistenceAdapter: NotificationMongoOutboxPersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val requestedAt = Instant.parse("2026-06-16T00:00:00Z")
    private val transitionAt = Instant.parse("2026-06-16T00:00:10Z")

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(Query(), NotificationOutboxDocument::class.java).block()
        mongoTemplate.remove(Query(), NotificationDocument::class.java).block()
    }

    @Test
    @DisplayName("발행 성공 결과로 outbox와 notification을 하나의 transaction에 저장한다")
    fun savePublished() = runBlocking {
        val notification = notification("request-1")
        val outbox = publishingOutbox(notification.id).markPublished(transitionAt)
        notificationPersistenceAdapter.save(notification)

        adapter.savePublished(outbox, transitionAt)

        val savedOutbox = outboxPersistenceAdapter.findById(outbox.id)
        val savedNotification = notificationPersistenceAdapter.findById(notification.id)

        assertNotNull(savedOutbox)
        assertNotNull(savedNotification)
        assertEquals(NotificationOutboxStatus.PUBLISHED, savedOutbox.outboxStatus)
        assertEquals(NotificationStatus.PUBLISHED, savedNotification.status)
    }

    @Test
    @DisplayName("발행 실패 결과로 outbox retry 상태와 notification 실패 상태를 하나의 transaction에 저장한다")
    fun savePublishFailed() = runBlocking {
        val notification = notification("request-2")
        val outbox = publishingOutbox(notification.id).recordFailure(
            reason = "broker-down",
            retryPolicy = RetryPolicy(
                maxAttempts = 3,
                baseDelay = Duration.ofSeconds(10),
                maxDelay = Duration.ofMinutes(1),
            ),
            now = transitionAt,
        )
        notificationPersistenceAdapter.save(notification)

        adapter.savePublishFailed(outbox, transitionAt, "broker-down")

        val savedOutbox = outboxPersistenceAdapter.findById(outbox.id)
        val savedNotification = notificationPersistenceAdapter.findById(notification.id)

        assertNotNull(savedOutbox)
        assertNotNull(savedNotification)
        assertEquals(NotificationOutboxStatus.PENDING, savedOutbox.outboxStatus)
        assertEquals("broker-down", savedOutbox.lastError)
        assertEquals(NotificationStatus.PUBLISH_FAILED, savedNotification.status)
        assertEquals("broker-down", savedNotification.failureReason)
    }

    @Test
    @DisplayName("notification 조회 실패 시 outbox 저장도 rollback한다")
    fun rollbackWhenNotificationNotFound() = runBlocking {
        val outbox = publishingOutbox(NotificationId.newId()).markPublished(transitionAt)

        assertFailsWith<IllegalStateException> {
            adapter.savePublished(outbox, transitionAt)
        }

        assertEquals(null, outboxPersistenceAdapter.findById(outbox.id))
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

    private fun publishingOutbox(notificationId: NotificationId): NotificationOutbox {
        return NotificationOutbox.create(
            notificationId = notificationId,
            topic = "notification.dispatch",
            partitionKey = "C123",
            eventPayload = """{"notificationId":"${notificationId.id}"}""",
            now = requestedAt,
        ).markPublishing(transitionAt.minusSeconds(1), "publisher-1")
    }
}
