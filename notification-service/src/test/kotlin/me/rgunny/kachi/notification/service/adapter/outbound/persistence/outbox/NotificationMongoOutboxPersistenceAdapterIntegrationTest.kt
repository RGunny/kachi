package me.rgunny.kachi.notification.service.adapter.outbound.persistence.outbox

import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxStatus
import me.rgunny.kachi.notification.domain.retry.RetryPolicy
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.outbox.NotificationOutboxDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query

@DisplayName("NotificationMongoOutboxPersistenceAdapter 통합 테스트")
class NotificationMongoOutboxPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: NotificationMongoOutboxPersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val createdAt = Instant.parse("2026-06-16T00:00:00Z")

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(Query(), NotificationOutboxDocument::class.java).block()
    }

    @Nested
    @DisplayName("save/find")
    inner class SaveFind {

        @Test
        @DisplayName("NotificationOutbox aggregate를 저장하고 조회한다")
        fun saveAndFind() = runBlocking {
            val outbox = outbox()

            val saved = adapter.save(outbox)
            val found = adapter.findById(outbox.id)

            assertEquals(outbox.id, saved.id)
            assertNotNull(found)
            assertEquals(outbox.id, found.id)
            assertEquals(NotificationOutboxStatus.PENDING, found.outboxStatus)
        }
    }

    @Nested
    @DisplayName("findPublishable()")
    inner class FindPublishable {

        @Test
        @DisplayName("PENDING이고 nextRetryAt이 지난 outbox만 조회한다")
        fun findPublishable() = runBlocking {
            val due = outbox(partitionKey = "due")
            val publishing = outbox(partitionKey = "publishing")
                .markPublishing(Instant.parse("2026-06-16T00:00:01Z"), "publisher-1")
            adapter.save(due)
            adapter.save(publishing)

            val result = adapter.findPublishable(Instant.parse("2026-06-16T00:00:10Z"), 10)

            assertEquals(listOf(due.id), result.map { it.id })
        }
    }

    @Nested
    @DisplayName("findDead()")
    inner class FindDead {

        @Test
        @DisplayName("DEAD outbox만 조회한다")
        fun findDead() = runBlocking {
            val dead = outbox(partitionKey = "dead")
                .markPublishing(Instant.parse("2026-06-16T00:00:01Z"), "publisher-1")
                .recordFailure(
                    reason = "broker-down",
                    retryPolicy = RetryPolicy(
                        maxAttempts = 1,
                        baseDelay = Duration.ofSeconds(1),
                        maxDelay = Duration.ofSeconds(1),
                    ),
                    now = Instant.parse("2026-06-16T00:00:02Z"),
                )
            val pending = outbox(partitionKey = "pending")
            adapter.save(dead)
            adapter.save(pending)

            val result = adapter.findDead(batchSize = 10)

            assertEquals(listOf(dead.id), result.map { it.id })
        }
    }

    @Nested
    @DisplayName("claimPublishing()")
    inner class ClaimPublishing {

        @Test
        @DisplayName("PENDING outbox만 PUBLISHING으로 원자 claim한다")
        fun claimPublishing() = runBlocking {
            val outbox = outbox()
            val claimedAt = Instant.parse("2026-06-16T00:00:10Z")
            adapter.save(outbox)

            val claimed = adapter.claimPublishing(outbox.id, "publisher-1", claimedAt)
            val secondClaim = adapter.claimPublishing(outbox.id, "publisher-2", claimedAt)

            assertNotNull(claimed)
            assertEquals(NotificationOutboxStatus.PUBLISHING, claimed.outboxStatus)
            assertEquals(claimedAt, claimed.claimedAt)
            assertEquals("publisher-1", claimed.claimedBy)
            assertNull(secondClaim)
        }

        @Test
        @DisplayName("nextRetryAt이 아직 도래하지 않은 PENDING outbox는 claim하지 않는다")
        fun doNotClaimBeforeNextRetryAt() = runBlocking {
            val claimAt = Instant.parse("2026-06-16T00:00:10Z")
            val outbox = outbox()
                .markPublishing(createdAt.plusSeconds(1), "publisher-1")
                .recordFailure(
                    reason = "broker-timeout",
                    retryPolicy = RetryPolicy(
                        maxAttempts = 3,
                        baseDelay = Duration.ofMinutes(1),
                        maxDelay = Duration.ofMinutes(1),
                    ),
                    now = createdAt,
                )
            adapter.save(outbox)

            val claimed = adapter.claimPublishing(outbox.id, "publisher-2", claimAt)

            assertNull(claimed)
            assertEquals(NotificationOutboxStatus.PENDING, adapter.findById(outbox.id)?.outboxStatus)
        }
    }

    @Nested
    @DisplayName("findStalePublishing()")
    inner class FindStalePublishing {

        @Test
        @DisplayName("threshold보다 오래된 PUBLISHING outbox만 조회한다")
        fun findStalePublishing() = runBlocking {
            val stale = outbox(partitionKey = "stale")
                .markPublishing(Instant.parse("2026-06-16T00:00:10Z"), "publisher-1")
            val fresh = outbox(partitionKey = "fresh")
                .markPublishing(Instant.parse("2026-06-16T00:00:50Z"), "publisher-1")
            adapter.save(stale)
            adapter.save(fresh)

            val result = adapter.findStalePublishing(
                threshold = Instant.parse("2026-06-16T00:00:30Z"),
                batchSize = 10,
            )

            assertEquals(listOf(stale.id), result.map { it.id })
        }
    }

    private fun outbox(partitionKey: String = "user-1"): NotificationOutbox {
        return NotificationOutbox.create(
            notificationId = NotificationId.newId(),
            topic = "notification.dispatch",
            partitionKey = partitionKey,
            eventPayload = """{"notificationId":"n1"}""",
            now = createdAt,
        )
    }
}
