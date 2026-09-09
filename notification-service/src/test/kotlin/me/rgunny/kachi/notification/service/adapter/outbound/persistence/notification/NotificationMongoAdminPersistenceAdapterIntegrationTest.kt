package me.rgunny.kachi.notification.service.adapter.outbound.persistence.notification

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOrigin
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.notification.NotificationDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.notification.NotificationHistoryDocument
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.outbox.NotificationMongoOutboxPersistenceAdapter
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.outbox.NotificationOutboxDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

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
    @DisplayName("DEAD notification 목록을 최신 변경순으로 조회한다")
    fun findDead() = runBlocking {
        val older = notificationPersistenceAdapter.save(deadNotification("request-old", NOW.plusSeconds(10)))
        val newer = notificationPersistenceAdapter.save(deadNotification("request-new", NOW.plusSeconds(20)))
        notificationPersistenceAdapter.save(requestedNotification("request-live"))

        val result = adapter.findDead(batchSize = 10)

        assertEquals(listOf(newer.id, older.id), result.map { it.id })
    }

    @Test
    @DisplayName("notification 상태 전이 history를 생성 시각 오름차순으로 조회한다")
    fun findHistories() = runBlocking {
        val notification = notificationPersistenceAdapter.save(deadNotification("request-history", NOW.plusSeconds(10)))

        val result = adapter.findHistories(notification.id, batchSize = 10)

        assertEquals(4, result.size)
        assertEquals(NotificationStatus.REQUESTED, result.first().fromStatus)
        assertEquals(NotificationStatus.DEAD, result.last().toStatus)
    }

    @Test
    @DisplayName("DEAD notification을 REQUESTED로 복구하고 새 outbox와 history를 transaction으로 저장한다")
    fun recoverDeadToRequested() = runBlocking {
        val notification = notificationPersistenceAdapter.save(deadNotification())
        val recovered = notification.recoverDeadToRequested(NOW.plusSeconds(5), "operator retry")
        val outbox = outbox(recovered.id)

        val saved = adapter.recoverDeadToRequested(recovered, outbox)

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
        return deadNotification("request-1", NOW.plusSeconds(4))
    }

    private fun deadNotification(requestId: String, deadAt: java.time.Instant): Notification {
        return requestedNotification(requestId)
            .markPublished(deadAt.minusSeconds(3))
            .markProcessing(deadAt.minusSeconds(2), "worker-1")
            .markFailed(deadAt.minusSeconds(1), "invalid recipient")
            .markDead(deadAt, "invalid recipient")
    }

    private fun requestedNotification(requestId: String = "request-1"): Notification {
        return Notification.request(
            requestId = requestId,
            requester = "collector-service",
            channel = NotificationChannel.SLACK,
            recipientId = "user-1",
            message = "hello",
            origin = NotificationOrigin.NONE,
            now = NOW,
        )
    }

    private fun outbox(notificationId: NotificationId): NotificationOutbox {
        return NotificationOutbox.create(
            notificationId = notificationId,
            topic = "notification.dispatch",
            partitionKey = "user-1",
            eventPayload = """{"notificationId":"${notificationId.id}"}""",
            now = NOW.plusSeconds(5),
        )
    }

    private companion object {
        val NOW = java.time.Instant.parse("2026-07-13T00:00:00Z")
    }
}
