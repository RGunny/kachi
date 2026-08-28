package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxId
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.rgunny.kachi.notification.domain.NotificationOrigin

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
    private val claimedAt = Instant.parse("2026-06-16T00:00:09Z")
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
        notificationPersistenceAdapter.save(notification)
        val claimed = claimedOutbox(notification.id)

        val finalized = adapter.savePublished(
            outbox = claimed.markPublished(transitionAt),
            expectedClaimedAt = claimedAt,
            expectedClaimedBy = PUBLISHER,
            now = transitionAt,
        )

        val savedOutbox = outboxPersistenceAdapter.findById(claimed.id)
        val savedNotification = notificationPersistenceAdapter.findById(notification.id)

        assertTrue(finalized)
        assertNotNull(savedOutbox)
        assertNotNull(savedNotification)
        assertEquals(NotificationOutboxStatus.PUBLISHED, savedOutbox.outboxStatus)
        assertNull(savedOutbox.claimedAt)
        assertNull(savedOutbox.claimedBy)
        assertEquals(NotificationStatus.PUBLISHED, savedNotification.status)
    }

    @Test
    @DisplayName("발행 실패 결과로 outbox retry 상태와 notification 실패 상태를 하나의 transaction에 저장한다")
    fun savePublishFailed() = runBlocking {
        val notification = notification("request-2")
        notificationPersistenceAdapter.save(notification)
        val claimed = claimedOutbox(notification.id)

        val finalized = adapter.savePublishFailed(
            outbox = claimed.recordFailure(
                reason = "broker-down",
                retryPolicy = RetryPolicy(
                    maxAttempts = 3,
                    baseDelay = Duration.ofSeconds(10),
                    maxDelay = Duration.ofMinutes(1),
                ),
                now = transitionAt,
            ),
            expectedClaimedAt = claimedAt,
            expectedClaimedBy = PUBLISHER,
            now = transitionAt,
            reason = "broker-down",
        )

        val savedOutbox = outboxPersistenceAdapter.findById(claimed.id)
        val savedNotification = notificationPersistenceAdapter.findById(notification.id)

        assertTrue(finalized)
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
        val claimed = claimedOutbox(NotificationId.newId())

        assertFailsWith<IllegalStateException> {
            adapter.savePublished(
                outbox = claimed.markPublished(transitionAt),
                expectedClaimedAt = claimedAt,
                expectedClaimedBy = PUBLISHER,
                now = transitionAt,
            )
        }

        val savedOutbox = outboxPersistenceAdapter.findById(claimed.id)
        assertNotNull(savedOutbox)
        assertEquals(NotificationOutboxStatus.PUBLISHING, savedOutbox.outboxStatus)
    }

    @Test
    @DisplayName("다른 publisher가 회수해 claimedBy가 바뀌었으면 발행 결과를 반영하지 않는다")
    fun ignoreWhenClaimedByChanged() = runBlocking {
        val notification = notification("request-3")
        notificationPersistenceAdapter.save(notification)
        val claimed = claimedOutbox(notification.id)

        val finalized = adapter.savePublished(
            outbox = claimed.markPublished(transitionAt),
            expectedClaimedAt = claimedAt,
            expectedClaimedBy = "publisher-2",
            now = transitionAt,
        )

        assertFalse(finalized)
        assertUnchanged(claimed.id, notification.id)
    }

    @Test
    @DisplayName("회수 후 다시 claim되어 claimedAt이 바뀌었으면 발행 결과를 반영하지 않는다")
    fun ignoreWhenClaimedAtChanged() = runBlocking {
        val notification = notification("request-4")
        notificationPersistenceAdapter.save(notification)
        val claimed = claimedOutbox(notification.id)

        val finalized = adapter.savePublished(
            outbox = claimed.markPublished(transitionAt),
            expectedClaimedAt = claimedAt.minusSeconds(5),
            expectedClaimedBy = PUBLISHER,
            now = transitionAt,
        )

        assertFalse(finalized)
        assertUnchanged(claimed.id, notification.id)
    }

    @Test
    @DisplayName("이미 PENDING으로 회수된 outbox에는 늦게 도착한 발행 결과를 반영하지 않는다")
    fun ignoreWhenAlreadyRecovered() = runBlocking {
        val notification = notification("request-5")
        notificationPersistenceAdapter.save(notification)
        val claimed = claimedOutbox(notification.id)
        // 발행이 길어지는 사이 다음 tick이 timeout으로 회수해 PENDING으로 되돌린 상태
        outboxPersistenceAdapter.save(
            claimed.recordFailure(
                reason = "publishing-timeout",
                retryPolicy = RetryPolicy(
                    maxAttempts = 3,
                    baseDelay = Duration.ofSeconds(10),
                    maxDelay = Duration.ofMinutes(1),
                ),
                now = transitionAt.minusSeconds(1),
            )
        )

        val finalized = adapter.savePublished(
            outbox = claimed.markPublished(transitionAt),
            expectedClaimedAt = claimedAt,
            expectedClaimedBy = PUBLISHER,
            now = transitionAt,
        )

        val savedOutbox = outboxPersistenceAdapter.findById(claimed.id)
        val savedNotification = notificationPersistenceAdapter.findById(notification.id)

        assertFalse(finalized)
        assertNotNull(savedOutbox)
        assertNotNull(savedNotification)
        assertEquals(NotificationOutboxStatus.PENDING, savedOutbox.outboxStatus)
        assertEquals("publishing-timeout", savedOutbox.lastError)
        assertEquals(NotificationStatus.REQUESTED, savedNotification.status)
    }

    private suspend fun assertUnchanged(outboxId: NotificationOutboxId, notificationId: NotificationId) {
        val savedOutbox = outboxPersistenceAdapter.findById(outboxId)
        val savedNotification = notificationPersistenceAdapter.findById(notificationId)

        assertNotNull(savedOutbox)
        assertNotNull(savedNotification)
        assertEquals(NotificationOutboxStatus.PUBLISHING, savedOutbox.outboxStatus)
        assertEquals(claimedAt, savedOutbox.claimedAt)
        assertEquals(PUBLISHER, savedOutbox.claimedBy)
        assertEquals(NotificationStatus.REQUESTED, savedNotification.status)
    }

    private fun notification(requestId: String): Notification {
        return Notification.request(
            requestId = requestId,
            requester = "collector-service",
            channel = NotificationChannel.SLACK,
            recipient = "C123",
            message = "hello",
            origin = NotificationOrigin.NONE,
            now = requestedAt,
        )
    }

    /**
     * 저장소 CAS로 실제 claim까지 마친 PUBLISHING outbox를 만든다. finalize 조건이 보는 것은 이 claim이다.
     */
    private suspend fun claimedOutbox(notificationId: NotificationId): NotificationOutbox {
        val outbox = NotificationOutbox.create(
            notificationId = notificationId,
            topic = "notification.dispatch",
            partitionKey = "C123",
            eventPayload = """{"notificationId":"${notificationId.id}"}""",
            now = requestedAt,
        )
        outboxPersistenceAdapter.save(outbox)

        return outboxPersistenceAdapter.claimPublishing(outbox.id, PUBLISHER, claimedAt)!!
    }

    private companion object {
        const val PUBLISHER = "publisher-1"
    }
}
