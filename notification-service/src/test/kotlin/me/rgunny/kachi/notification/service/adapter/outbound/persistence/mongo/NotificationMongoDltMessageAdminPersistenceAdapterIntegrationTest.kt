package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.service.adapter.outbound.persistence.document.NotificationDltMessageDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("NotificationMongoDltMessageAdminPersistenceAdapter 통합 테스트")
class NotificationMongoDltMessageAdminPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: NotificationMongoDltMessageAdminPersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(Query(), NotificationDltMessageDocument::class.java).block()
    }

    @Test
    @DisplayName("PENDING DLT 메시지를 실패 시각 최신순으로 조회한다")
    fun findByStatus() = runBlocking {
        val older = document(originalOffset = 100, deadLetteredAt = NOW.minusSeconds(60))
        val newer = document(originalOffset = 101, deadLetteredAt = NOW)
        val discarded = document(
            originalOffset = 102,
            deadLetteredAt = NOW.plusSeconds(60),
            status = NotificationDltMessageStatus.DISCARDED,
            discardedAt = NOW.plusSeconds(70),
            discardReason = "operator discard",
        )
        mongoTemplate.insertAll(listOf(older, newer, discarded)).collectList().block()

        val result = adapter.findByStatus(NotificationDltMessageStatus.PENDING, batchSize = 10)

        assertEquals(listOf(101L, 100L), result.map { it.originalOffset })
        assertEquals(listOf(NotificationDltMessageStatus.PENDING, NotificationDltMessageStatus.PENDING), result.map { it.status })
    }

    @Test
    @DisplayName("DLT 메시지를 id로 조회한다")
    fun findById() = runBlocking {
        val document = document(originalOffset = 100, deadLetteredAt = NOW)
        mongoTemplate.insert(document).block()

        val result = adapter.findById(NotificationDltMessageId.of(UUID.fromString(document.id)))

        assertNotNull(result)
        assertEquals(100, result.originalOffset)
        assertEquals("""{"notificationId":"n100"}""", result.payload)
    }

    @Test
    @DisplayName("존재하지 않는 DLT 메시지는 null을 반환한다")
    fun findByIdNotFound() = runBlocking {
        val result = adapter.findById(NotificationDltMessageId.fromOriginalRecord("notification.dispatch", 0, 404))

        assertNull(result)
    }

    @Test
    @DisplayName("PENDING DLT 메시지를 DISCARDED로 조건부 갱신한다")
    fun discardIfPending() = runBlocking {
        val document = document(originalOffset = 100, deadLetteredAt = NOW)
        mongoTemplate.insert(document).block()
        val message = adapter.findById(NotificationDltMessageId.of(UUID.fromString(document.id)))
        assertNotNull(message)
        message.discard(NOW.plusSeconds(10), "operator discard")

        val result = adapter.discardIfPending(message)

        assertNotNull(result)
        assertEquals(NotificationDltMessageStatus.DISCARDED, result.status)
        assertEquals(NOW.plusSeconds(10), result.discardedAt)
        assertEquals("operator discard", result.discardReason)
    }

    @Test
    @DisplayName("PENDING 조건이 불일치하면 DLT 메시지를 폐기하지 않는다")
    fun discardIfPendingMismatch() = runBlocking {
        val document = document(
            originalOffset = 100,
            deadLetteredAt = NOW,
            status = NotificationDltMessageStatus.DISCARDED,
            discardedAt = NOW.plusSeconds(5),
            discardReason = "already discarded",
        )
        mongoTemplate.insert(document).block()
        val message = adapter.findById(NotificationDltMessageId.of(UUID.fromString(document.id)))
        assertNotNull(message)

        val result = adapter.discardIfPending(message)

        assertNull(result)
    }

    private fun document(
        originalOffset: Long,
        deadLetteredAt: Instant,
        status: NotificationDltMessageStatus = NotificationDltMessageStatus.PENDING,
        discardedAt: Instant? = null,
        discardReason: String? = null,
        reprocessedAt: Instant? = null,
        reprocessReason: String? = null,
    ): NotificationDltMessageDocument {
        return NotificationDltMessageDocument(
            id = UUID.nameUUIDFromBytes("notification.dispatch:0:$originalOffset".toByteArray()).toString(),
            originalTopic = "notification.dispatch",
            originalPartition = 0,
            originalOffset = originalOffset,
            originalTimestamp = deadLetteredAt.minusSeconds(1),
            dltTopic = "notification.dispatch.dlt",
            dltPartition = 0,
            dltOffset = originalOffset + 100,
            consumerGroup = "notification-worker",
            messageKey = "key-$originalOffset",
            payload = """{"notificationId":"n$originalOffset"}""",
            exceptionFqcn = "java.net.SocketTimeoutException",
            exceptionMessage = "timeout",
            deadLetteredAt = deadLetteredAt,
            storedAt = deadLetteredAt.plusSeconds(1),
            discardedAt = discardedAt,
            discardReason = discardReason,
            reprocessedAt = reprocessedAt,
            reprocessReason = reprocessReason,
            status = status.name,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-13T00:00:00Z")
    }
}
