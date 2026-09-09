package me.rgunny.kachi.collector.adapter.outbound.persistence.outbox

import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxClaim
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxEventType
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxId
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

@DisplayName("CollectorOutboxPersistenceAdapter 통합 테스트")
class CollectorOutboxPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: CollectorOutboxPersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val now = CollectorTestFixture.NOW

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(CollectorOutboxMongoDocument::class.java).all().block()
    }

    @Nested
    @DisplayName("save() / findById()")
    inner class SaveAndFindById {

        @Test
        @DisplayName("outbox 도메인을 저장하고 전 필드를 그대로 복원한다")
        fun saveAndRestoreAllFields() = runBlocking {
            val outbox = CollectorTestFixture.outbox(now = now)

            adapter.save(outbox)
            val found = adapter.findById(outbox.id)

            assertNotNull(found)
            assertEquals(outbox.id, found.id)
            assertEquals(CollectorOutboxEventType.NEWS_COLLECTED, found.eventType)
            assertEquals(CollectorTestFixture.OUTBOX_EVENT_KEY, found.eventKey)
            assertEquals(CollectorTestFixture.OUTBOX_EVENT_KEY, found.partitionKey)
            assertEquals(CollectorTestFixture.OUTBOX_PAYLOAD, found.payload)
            assertEquals(CollectorOutboxStatus.PENDING, found.status)
            assertEquals(0, found.retryCount)
            assertEquals(now, found.nextRetryAt)
            assertNull(found.lastError)
            assertNull(found.publishedAt)
            assertNull(found.claim)
            assertEquals(now, found.createdAt)
            assertEquals(now, found.updatedAt)
        }

        @Test
        @DisplayName("PUBLISHING 행의 소유자와 점유 시각은 두 필드로 저장되고 하나의 claim으로 복원된다")
        fun restoreClaimFromTwoFields() = runBlocking {
            val publishing = CollectorTestFixture.outbox(now = now).markPublishing(now = now, claimedBy = PUBLISHER)

            adapter.save(publishing)
            val found = adapter.findById(publishing.id)

            assertNotNull(found)
            assertEquals(CollectorOutboxStatus.PUBLISHING, found.status)
            assertEquals(CollectorOutboxClaim(claimedBy = PUBLISHER, claimedAt = now), found.claim)
        }

        @Test
        @DisplayName("없는 id를 조회하면 null이다")
        fun findByIdReturnsNullWhenAbsent() = runBlocking {
            assertNull(adapter.findById(CollectorOutboxId.newId()))
        }

        @Test
        @DisplayName("같은 eventKey는 두 번 저장할 수 없다")
        fun rejectDuplicateEventKey() = runBlocking {
            adapter.save(CollectorTestFixture.outbox(eventKey = "duplicated-key", now = now))

            assertFailsWith<DuplicateKeyException> {
                adapter.save(CollectorTestFixture.outbox(eventKey = "duplicated-key", now = now))
            }
            Unit
        }
    }

    @Nested
    @DisplayName("findPublishable()")
    inner class FindPublishable {

        @Test
        @DisplayName("차례가 된 PENDING 행만 nextRetryAt, createdAt 순으로 읽는다")
        fun findPendingInOrder() = runBlocking {
            val later = save(eventKey = "later", nextRetryAt = now, createdAt = now.plusSeconds(10))
            val earlier = save(eventKey = "earlier", nextRetryAt = now, createdAt = now)
            val first = save(eventKey = "first", nextRetryAt = now.minusSeconds(30), createdAt = now.plusSeconds(20))

            val publishable = adapter.findPublishable(now = now, batchSize = 10)

            assertEquals(listOf(first.id, earlier.id, later.id), publishable.map { it.id })
        }

        @Test
        @DisplayName("batchSize를 넘겨 읽지 않는다")
        fun limitByBatchSize() = runBlocking {
            save(eventKey = "a", nextRetryAt = now)
            save(eventKey = "b", nextRetryAt = now)

            assertEquals(1, adapter.findPublishable(now = now, batchSize = 1).size)
        }

        @Test
        @DisplayName("차례가 아닌 행과 PENDING이 아닌 행은 제외한다")
        fun excludeNotPublishable() = runBlocking {
            save(eventKey = "future", nextRetryAt = now.plusSeconds(1))
            save(eventKey = "publishing", status = CollectorOutboxStatus.PUBLISHING, claim = claim())
            save(eventKey = "published", status = CollectorOutboxStatus.PUBLISHED, publishedAt = now)
            save(eventKey = "dead", status = CollectorOutboxStatus.DEAD, lastError = "broker down")

            assertTrue(adapter.findPublishable(now = now, batchSize = 10).isEmpty())
        }
    }

    @Nested
    @DisplayName("claimPublishing()")
    inner class ClaimPublishing {

        @Test
        @DisplayName("PENDING 행을 PUBLISHING으로 옮기고 소유권을 돌려준다")
        fun claimPendingOutbox() = runBlocking {
            val pending = save(eventKey = "claimable", nextRetryAt = now)

            val claimed = adapter.claimPublishing(id = pending.id, claimedBy = PUBLISHER, now = now)

            assertNotNull(claimed)
            assertEquals(CollectorOutboxStatus.PUBLISHING, claimed.status)
            assertEquals(CollectorOutboxClaim(claimedBy = PUBLISHER, claimedAt = now), claimed.claim)
            assertEquals(claimed.claim, adapter.findById(pending.id)?.claim)
        }

        @Test
        @DisplayName("이미 잡혔거나 차례가 아니거나 없는 행은 잡지 못한다")
        fun rejectUnclaimable() = runBlocking {
            val publishing = save(eventKey = "publishing", status = CollectorOutboxStatus.PUBLISHING, claim = claim())
            val future = save(eventKey = "future", nextRetryAt = now.plusSeconds(1))

            assertNull(adapter.claimPublishing(id = publishing.id, claimedBy = "other", now = now))
            assertNull(adapter.claimPublishing(id = future.id, claimedBy = PUBLISHER, now = now))
            assertNull(adapter.claimPublishing(id = CollectorOutboxId.newId(), claimedBy = PUBLISHER, now = now))
        }

        @Test
        @DisplayName("같은 행을 동시에 잡으면 한쪽만 성공한다")
        fun onlyOneClaimSucceeds() = runBlocking {
            val pending = save(eventKey = "contested", nextRetryAt = now)

            val results = coroutineScope {
                listOf("publisher-1", "publisher-2").map { publisher ->
                    async(Dispatchers.IO) {
                        adapter.claimPublishing(id = pending.id, claimedBy = publisher, now = now)
                    }
                }.awaitAll()
            }

            assertEquals(1, results.count { it != null })
        }
    }

    @Nested
    @DisplayName("findStalePublishing()")
    inner class FindStalePublishing {

        @Test
        @DisplayName("기준 시각보다 오래 점유된 PUBLISHING 행만 오래된 순으로 읽는다")
        fun findStaleInOrder() = runBlocking {
            val oldest = save(
                eventKey = "oldest",
                status = CollectorOutboxStatus.PUBLISHING,
                claim = claim(claimedAt = now.minus(Duration.ofMinutes(5)))
            )
            val stale = save(
                eventKey = "stale",
                status = CollectorOutboxStatus.PUBLISHING,
                claim = claim(claimedAt = now.minus(Duration.ofMinutes(2)))
            )
            save(eventKey = "fresh", status = CollectorOutboxStatus.PUBLISHING, claim = claim(claimedAt = now))
            save(eventKey = "pending", nextRetryAt = now)

            val recovered = adapter.findStalePublishing(threshold = now.minusSeconds(60), batchSize = 10)

            assertEquals(listOf(oldest.id, stale.id), recovered.map { it.id })
        }

        @Test
        @DisplayName("batchSize를 넘겨 읽지 않는다")
        fun limitByBatchSize() = runBlocking {
            save(eventKey = "a", status = CollectorOutboxStatus.PUBLISHING, claim = claim(claimedAt = now.minusSeconds(300)))
            save(eventKey = "b", status = CollectorOutboxStatus.PUBLISHING, claim = claim(claimedAt = now.minusSeconds(200)))

            assertEquals(1, adapter.findStalePublishing(threshold = now.minusSeconds(60), batchSize = 1).size)
        }
    }

    @Nested
    @DisplayName("finalize()")
    inner class Finalize {

        @Test
        @DisplayName("소유권이 그대로면 발행 결과를 확정하고 소유권을 비운다")
        fun finalizeWithMatchingClaim() = runBlocking {
            val claimed = save(eventKey = "finalizable", status = CollectorOutboxStatus.PUBLISHING, claim = claim())
            val published = claimed.markPublished(now.plusSeconds(1))

            val finalized = adapter.finalize(published, expectedClaim = claim())

            assertTrue(finalized)
            val found = adapter.findById(claimed.id)
            assertNotNull(found)
            assertEquals(CollectorOutboxStatus.PUBLISHED, found.status)
            assertEquals(now.plusSeconds(1), found.publishedAt)
            assertEquals(now.plusSeconds(1), found.updatedAt)
            assertNull(found.claim)
        }

        @Test
        @DisplayName("소유권이나 상태가 어긋나면 확정하지 않고 문서를 그대로 둔다")
        fun rejectFinalizeWithStaleClaim() = runBlocking {
            val claimed = save(eventKey = "contested", status = CollectorOutboxStatus.PUBLISHING, claim = claim())
            val published = claimed.markPublished(now.plusSeconds(1))
            val pending = save(eventKey = "pending", nextRetryAt = now)

            assertFalse(adapter.finalize(published, expectedClaim = claim(claimedBy = "other")))
            assertFalse(adapter.finalize(published, expectedClaim = claim(claimedAt = now.plusSeconds(30))))
            assertFalse(adapter.finalize(publishedCopyOf(pending), expectedClaim = claim()))

            val found = adapter.findById(claimed.id)
            assertNotNull(found)
            assertEquals(CollectorOutboxStatus.PUBLISHING, found.status)
            assertEquals(claim(), found.claim)
        }
    }

    @Nested
    @DisplayName("findByStatus()")
    inner class FindByStatus {

        @ParameterizedTest
        @EnumSource(CollectorOutboxStatus::class)
        @DisplayName("요청한 상태의 행만 읽는다")
        fun findOnlyRequestedStatus(status: CollectorOutboxStatus) = runBlocking {
            val stored = CollectorOutboxStatus.entries.associateWith { saveWith(it) }

            assertEquals(
                listOf(stored.getValue(status).id),
                adapter.findByStatus(status, batchSize = 10).map { it.id }
            )
        }

        @Test
        @DisplayName("batchSize만큼만 오래된 순으로 읽는다")
        fun limitByBatchSize() = runBlocking {
            val older = save(
                eventKey = "dead-older",
                status = CollectorOutboxStatus.DEAD,
                nextRetryAt = now.minus(Duration.ofMinutes(1)),
                lastError = "broker down"
            )
            save(eventKey = "dead-newer", status = CollectorOutboxStatus.DEAD, lastError = "broker down")

            assertEquals(listOf(older.id), adapter.findByStatus(CollectorOutboxStatus.DEAD, batchSize = 1).map { it.id })
            assertEquals(2, adapter.findByStatus(CollectorOutboxStatus.DEAD, batchSize = 10).size)
        }
    }

    @Nested
    @DisplayName("recoverDead()")
    inner class RecoverDead {

        @Test
        @DisplayName("DEAD 행을 발행 대기 상태로 되돌린다")
        fun recoverOnlyDead() = runBlocking {
            val dead = save(eventKey = "dead", status = CollectorOutboxStatus.DEAD, lastError = "broker down")

            assertTrue(adapter.recoverDead(dead.recoverToPending(now)))

            val found = assertNotNull(adapter.findById(dead.id))
            assertEquals(CollectorOutboxStatus.PENDING, found.status)
            assertEquals(0, found.retryCount)
            assertEquals(now, found.nextRetryAt)
            assertNull(found.lastError)
            assertNull(found.claim)
        }

        @ParameterizedTest
        @EnumSource(value = CollectorOutboxStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["DEAD"])
        @DisplayName("DEAD가 아닌 행은 바꾸지 않고 실패로 알린다")
        fun rejectNotDead(status: CollectorOutboxStatus) = runBlocking {
            val stored = saveWith(status)
            val recovered = CollectorTestFixture.restoredOutbox(
                id = stored.id,
                eventKey = stored.eventKey,
                status = CollectorOutboxStatus.PENDING,
                nextRetryAt = now,
                createdAt = stored.createdAt,
                updatedAt = now
            )

            assertFalse(adapter.recoverDead(recovered))

            val found = assertNotNull(adapter.findById(stored.id))
            assertEquals(status, found.status)
            assertEquals(stored.updatedAt, found.updatedAt)
        }

        @Test
        @DisplayName("같은 행을 두 번 복구하면 두 번째는 실패한다")
        fun recoverOnlyOnce() = runBlocking {
            val dead = save(eventKey = "dead", status = CollectorOutboxStatus.DEAD, lastError = "broker down")
            val recovered = dead.recoverToPending(now)

            assertTrue(adapter.recoverDead(recovered))
            assertFalse(adapter.recoverDead(recovered))
        }
    }

    @Nested
    @DisplayName("index")
    inner class Indexes {

        @Test
        @DisplayName("collection에 outbox index 3개가 만들어진다")
        fun createDeclaredIndexes() {
            val indexNames = mongoTemplate.indexOps(CollectorOutboxMongoDocument::class.java)
                .indexInfo
                .collectList()
                .block()
                .orEmpty()
                .map { it.name }

            assertTrue(
                indexNames.containsAll(
                    listOf(
                        "ux_collector_outbox_event_key",
                        "ix_collector_outbox_status_next_retry_at",
                        "ix_collector_outbox_status_claimed_at"
                    )
                ),
                "생성된 index: $indexNames"
            )
        }
    }

    /**
     * 상태별 행을 정렬·조건 검증에 필요한 값 그대로 만들어 저장한다.
     * 전이 메서드로는 nextRetryAt과 createdAt을 따로 지정할 수 없다.
     */
    private suspend fun save(
        eventKey: String,
        status: CollectorOutboxStatus = CollectorOutboxStatus.PENDING,
        nextRetryAt: Instant = now,
        createdAt: Instant = now,
        lastError: String? = null,
        publishedAt: Instant? = null,
        claim: CollectorOutboxClaim? = null
    ): CollectorOutbox {
        return adapter.save(
            CollectorTestFixture.restoredOutbox(
                eventKey = eventKey,
                status = status,
                nextRetryAt = nextRetryAt,
                lastError = lastError,
                publishedAt = publishedAt,
                claim = claim,
                createdAt = createdAt
            )
        )
    }

    /**
     * 상태 하나에 맞는 행을 그 상태가 요구하는 필드까지 채워 저장한다.
     */
    private suspend fun saveWith(status: CollectorOutboxStatus): CollectorOutbox {
        return save(
            eventKey = "outbox-${status.name.lowercase()}",
            status = status,
            lastError = "broker down".takeIf { status == CollectorOutboxStatus.DEAD },
            publishedAt = now.takeIf { status == CollectorOutboxStatus.PUBLISHED },
            claim = claim().takeIf { status == CollectorOutboxStatus.PUBLISHING }
        )
    }

    private fun claim(
        claimedBy: String = PUBLISHER,
        claimedAt: Instant = now
    ): CollectorOutboxClaim {
        return CollectorOutboxClaim(claimedBy = claimedBy, claimedAt = claimedAt)
    }

    /**
     * PENDING 행을 PUBLISHING을 거치지 않고 PUBLISHED로 바꾼 사본. 상태가 어긋난 finalize 요청을 만들 때 쓴다.
     */
    private fun publishedCopyOf(outbox: CollectorOutbox): CollectorOutbox {
        return CollectorTestFixture.restoredOutbox(
            id = outbox.id,
            eventKey = outbox.eventKey,
            status = CollectorOutboxStatus.PUBLISHED,
            publishedAt = now,
            createdAt = outbox.createdAt,
            updatedAt = now
        )
    }

    private companion object {
        const val PUBLISHER = "publisher-1"
    }
}
