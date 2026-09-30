package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.notification

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationOrigin
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.notification.NotificationDocument
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.notification.NotificationHistoryDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query

@DisplayName("worker NotificationMongoDispatchPersistenceAdapter 통합 테스트")
class NotificationMongoDispatchPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: NotificationMongoDispatchPersistenceAdapter

    @Autowired
    private lateinit var notificationPersistenceAdapter: NotificationMongoPersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val requestedAt = Instant.parse("2026-06-19T00:00:00Z")
    private val transitionAt = Instant.parse("2026-06-19T00:00:30Z")

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(Query(), NotificationDocument::class.java).block()
        mongoTemplate.remove(Query(), NotificationHistoryDocument::class.java).block()
    }

    @Test
    @DisplayName("vendor 발송 성공 결과를 SENT 상태로 저장한다")
    fun saveSent() = runBlocking {
        val notification = notificationPersistenceAdapter.save(processingNotification())
        val sent = notification.markSent(transitionAt)

        val saved = adapter.saveFinalizedIfProcessingClaimMatches(
            notification = sent,
            expectedClaimedAt = requireNotNull(notification.claimedAt),
            expectedClaimedBy = requireNotNull(notification.claimedBy),
        )

        val found = notificationPersistenceAdapter.findById(notification.id)
        assertNotNull(saved)
        assertEquals(NotificationStatus.SENT, saved.status)
        assertNotNull(found)
        assertEquals(NotificationStatus.SENT, found.status)
        assertEquals(1, found.dispatchAttempts)
        assertEquals(3, historyCount(notification))
    }

    @Test
    @DisplayName("vendor 재시도 가능 실패 결과를 RETRY_WAIT 상태로 저장한다")
    fun saveRetryWait() = runBlocking {
        val notification = notificationPersistenceAdapter.save(processingNotification())
        val retryWait = notification
            .markFailed(transitionAt, "vendor timeout")
            .markRetryWait(transitionAt, "vendor timeout")

        val saved = adapter.saveFinalizedIfProcessingClaimMatches(
            notification = retryWait,
            expectedClaimedAt = requireNotNull(notification.claimedAt),
            expectedClaimedBy = requireNotNull(notification.claimedBy),
        )

        val found = notificationPersistenceAdapter.findById(notification.id)
        assertNotNull(saved)
        assertEquals(NotificationStatus.RETRY_WAIT, saved.status)
        assertNotNull(found)
        assertEquals(NotificationStatus.RETRY_WAIT, found.status)
        assertEquals("vendor timeout", found.failureReason)
        assertEquals(1, found.dispatchAttempts)
        assertEquals(4, historyCount(notification))
    }

    @Test
    @DisplayName("vendor 영구 실패 결과를 DEAD 상태로 저장한다")
    fun saveDead() = runBlocking {
        val notification = notificationPersistenceAdapter.save(processingNotification())
        val dead = notification
            .markFailed(transitionAt, "invalid recipient")
            .markDead(transitionAt, "invalid recipient")

        val saved = adapter.saveFinalizedIfProcessingClaimMatches(
            notification = dead,
            expectedClaimedAt = requireNotNull(notification.claimedAt),
            expectedClaimedBy = requireNotNull(notification.claimedBy),
        )

        val found = notificationPersistenceAdapter.findById(notification.id)
        assertNotNull(saved)
        assertEquals(NotificationStatus.DEAD, saved.status)
        assertNotNull(found)
        assertEquals(NotificationStatus.DEAD, found.status)
        assertEquals("invalid recipient", found.failureReason)
        assertEquals(1, found.dispatchAttempts)
        assertEquals(4, historyCount(notification))
    }

    @Test
    @DisplayName("PROCESSING claim 조건이 불일치하면 저장하지 않고 null을 반환한다")
    fun skipWhenClaimMismatch() = runBlocking {
        val notification = notificationPersistenceAdapter.save(processingNotification())
        val expectedClaimedAt = requireNotNull(notification.claimedAt)
        val expectedClaimedBy = requireNotNull(notification.claimedBy)
        val sent = notification.markSent(transitionAt)

        val saved = adapter.saveFinalizedIfProcessingClaimMatches(
            notification = sent,
            expectedClaimedAt = expectedClaimedAt.plusSeconds(1),
            expectedClaimedBy = expectedClaimedBy,
        )

        val found = notificationPersistenceAdapter.findById(notification.id)
        assertNull(saved)
        assertNotNull(found)
        assertEquals(NotificationStatus.PROCESSING, found.status)
        assertEquals(expectedClaimedAt, found.claimedAt)
        assertEquals(expectedClaimedBy, found.claimedBy)
        assertEquals(2, historyCount(notification))
    }

    private fun historyCount(notification: Notification): Int {
        return mongoTemplate.count(
            Query.query(
                org.springframework.data.mongodb.core.query.Criteria
                    .where("notificationId")
                    .`is`(notification.id.id.toString())
            ),
            NotificationHistoryDocument::class.java,
        ).block()?.toInt() ?: 0
    }

    private fun processingNotification(): Notification {
        return Notification.request(
            requestId = "request-1",
            requester = "collector-service",
            channel = NotificationChannel.SLACK,
            recipientId = "user-1",
            message = "hello",
            origin = NotificationOrigin.NONE,
            now = requestedAt,
        )
            .markPublished(requestedAt.plusSeconds(10))
            .markProcessing(requestedAt.plusSeconds(20), "worker-1")
    }
}
