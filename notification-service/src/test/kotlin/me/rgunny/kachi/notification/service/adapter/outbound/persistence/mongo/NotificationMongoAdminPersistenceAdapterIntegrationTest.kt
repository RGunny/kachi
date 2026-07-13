package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationHistoryDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationOutboxDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("NotificationMongoAdminPersistenceAdapter 통합 테스트")
class NotificationMongoAdminPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: NotificationMongoAdminPersistenceAdapter

    @Autowired
    private lateinit var notificationPersistenceAdapter: NotificationMongoPersistenceAdapter

    @Autowired
    private lateinit var outboxPersistenceAdapter: NotificationMongoOutboxPersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(Query(), NotificationOutboxDocument::class.java).block()
        mongoTemplate.remove(Query(), NotificationHistoryDocument::class.java).block()
        mongoTemplate.remove(Query(), NotificationDocument::class.java).block()
    }

    @Test
    @DisplayName("DEAD notification을 REQUESTED로 복구하고 새 outbox와 history를 transaction으로 저장한다")
    fun recoverDeadToRequested() = runBlocking {
        val notification = notificationPersistenceAdapter.save(deadNotification())
        notification.recoverDeadToRequested(NOW.plusSeconds(5), "operator retry")
        val outbox = outbox(notification.id)

        val saved = adapter.recoverDeadToRequested(notification, outbox)

        val found = notificationPersistenceAdapter.findById(notification.id)
        assertNotNull(saved)
        assertNotNull(found)
        assertEquals(NotificationStatus.REQUESTED, found.status)
        assertEquals(null, found.failureReason)
        assertEquals(0, found.dispatchAttempts)
        assertNotNull(outboxPersistenceAdapter.findById(outbox.id))
        assertEquals(5, historyCount(notification.id))
    }

    @Test
    @DisplayName("DEAD 조건이 불일치하면 outbox와 history를 저장하지 않는다")
    fun skipWhenStatusMismatch() = runBlocking {
        val notification = requestedNotification()
        notificationPersistenceAdapter.save(notification)
        val outbox = outbox(notification.id)

        val saved = adapter.recoverDeadToRequested(notification, outbox)

        assertNull(saved)
        assertNull(outboxPersistenceAdapter.findById(outbox.id))
        assertEquals(0, historyCount(notification.id))
    }

    private fun historyCount(notificationId: NotificationId): Int {
        return mongoTemplate.count(
            Query.query(Criteria.where("notificationId").`is`(notificationId.id.toString())),
            NotificationHistoryDocument::class.java,
        ).block()?.toInt() ?: 0
    }

    private fun deadNotification(): Notification {
        return requestedNotification().also {
            it.markPublished(NOW.plusSeconds(1))
            it.markProcessing(NOW.plusSeconds(2), "worker-1")
            it.markFailed(NOW.plusSeconds(3), "invalid recipient")
            it.markDead(NOW.plusSeconds(4), "invalid recipient")
        }
    }

    private fun requestedNotification(): Notification {
        return Notification.request(
            requestId = "request-1",
            requester = "collector-service",
            channel = NotificationChannel.SLACK,
            recipient = "C123",
            message = "hello",
            now = NOW,
        )
    }

    private fun outbox(notificationId: NotificationId): NotificationOutbox {
        return NotificationOutbox.create(
            notificationId = notificationId,
            topic = "notification.dispatch",
            partitionKey = "C123",
            eventPayload = """{"notificationId":"${notificationId.id}"}""",
            now = NOW.plusSeconds(5),
        )
    }

    private companion object {
        val NOW = java.time.Instant.parse("2026-07-13T00:00:00Z")
    }
}
