package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mongo

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.document.NotificationDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("worker NotificationMongoDispatchPersistenceAdapter 통합 테스트")
class NotificationMongoDispatchPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: NotificationMongoDispatchPersistenceAdapter

    @Autowired
    private lateinit var notificationPersistenceAdapter: NotificationMongoPersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val requestedAt = Instant.parse("2026-06-19T00:00:00Z")
    private val handledAt = Instant.parse("2026-06-19T00:00:30Z")

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(Query(), NotificationDocument::class.java).block()
    }

    @Test
    @DisplayName("vendor 발송 성공 결과를 SENT 상태로 저장한다")
    fun saveSent() = runBlocking {
        val notification = processingNotification().also {
            it.markSent(handledAt)
        }

        val saved = adapter.saveFinalized(notification)

        val found = notificationPersistenceAdapter.findById(notification.id)
        assertEquals(NotificationStatus.SENT, saved.status)
        assertNotNull(found)
        assertEquals(NotificationStatus.SENT, found.status)
        assertEquals(1, found.dispatchAttempts)
    }

    @Test
    @DisplayName("vendor 재시도 가능 실패 결과를 RETRY_WAIT 상태로 저장한다")
    fun saveRetryWait() = runBlocking {
        val notification = processingNotification().also {
            it.markFailed(handledAt, "vendor timeout")
            it.markRetryWait(handledAt, "vendor timeout")
        }

        val saved = adapter.saveFinalized(notification)

        val found = notificationPersistenceAdapter.findById(notification.id)
        assertEquals(NotificationStatus.RETRY_WAIT, saved.status)
        assertNotNull(found)
        assertEquals(NotificationStatus.RETRY_WAIT, found.status)
        assertEquals("vendor timeout", found.failureReason)
        assertEquals(1, found.dispatchAttempts)
    }

    @Test
    @DisplayName("vendor 영구 실패 결과를 DEAD 상태로 저장한다")
    fun saveDead() = runBlocking {
        val notification = processingNotification().also {
            it.markFailed(handledAt, "invalid recipient")
            it.markDead(handledAt, "invalid recipient")
        }

        val saved = adapter.saveFinalized(notification)

        val found = notificationPersistenceAdapter.findById(notification.id)
        assertEquals(NotificationStatus.DEAD, saved.status)
        assertNotNull(found)
        assertEquals(NotificationStatus.DEAD, found.status)
        assertEquals("invalid recipient", found.failureReason)
        assertEquals(1, found.dispatchAttempts)
    }

    private fun processingNotification(): Notification {
        return Notification.request(
            requestId = "request-1",
            requester = "collector-service",
            channel = NotificationChannel.SLACK,
            recipient = "C123",
            message = "hello",
            now = requestedAt,
        ).also {
            it.markPublished(requestedAt.plusSeconds(10))
            it.markProcessing(requestedAt.plusSeconds(20), "worker-1")
        }
    }
}
