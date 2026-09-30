package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.dlt

import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.dlt.NotificationDltMessageDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query

@DisplayName("NotificationMongoDltMessagePersistenceAdapter 통합 테스트")
class NotificationMongoDltMessagePersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: NotificationMongoDltMessagePersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(Query(), NotificationDltMessageDocument::class.java).block()
    }

    @Test
    @DisplayName("DLT 메시지를 PENDING 상태로 저장한다")
    fun save() = runBlocking {
        val saved = adapter.save(message())

        assertEquals(NotificationDltMessageStatus.PENDING, saved.status)
        assertEquals("notification.dispatch", saved.originalTopic)
        assertEquals(100, saved.originalOffset)
        assertEquals(1, count())
    }

    @Test
    @DisplayName("같은 원본 record 위치의 DLT 메시지는 기존 문서를 덮어쓰지 않는다")
    fun saveIdempotently() = runBlocking {
        adapter.save(message())
        adapter.save(message(dltOffset = 201))

        val documents = mongoTemplate.findAll(NotificationDltMessageDocument::class.java)
            .collectList()
            .block()
            .orEmpty()

        assertEquals(1, documents.size)
        assertEquals(200, documents.single().dltOffset)
    }

    @Test
    @DisplayName("이미 운영 처리된 DLT 메시지는 재소비되어도 PENDING으로 되돌리지 않는다")
    fun saveDoesNotOverwriteHandledMessage() = runBlocking {
        mongoTemplate.insert(discardedDocument()).block()

        val saved = adapter.save(message())

        assertEquals(NotificationDltMessageStatus.DISCARDED, saved.status)
        assertEquals(NOW.plusSeconds(10), saved.discardedAt)
        assertEquals("operator discard", saved.discardReason)
    }

    private fun count(): Int {
        return mongoTemplate.count(Query(), NotificationDltMessageDocument::class.java)
            .block()
            ?.toInt() ?: 0
    }

    private fun message(dltOffset: Long = 200): NotificationDltMessage {
        return NotificationDltMessage.record(
            originalTopic = "notification.dispatch",
            originalPartition = 0,
            originalOffset = 100,
            originalTimestamp = NOW.minusSeconds(1),
            dltTopic = "notification.dispatch.dlt",
            dltPartition = 0,
            dltOffset = dltOffset,
            consumerGroup = "notification-worker",
            messageKey = "key-1",
            payload = """{"notificationId":"n1"}""",
            exceptionFqcn = "java.net.SocketTimeoutException",
            exceptionMessage = "timeout",
            deadLetteredAt = NOW,
            storedAt = NOW.plusSeconds(1),
        )
    }

    private fun discardedDocument(): NotificationDltMessageDocument {
        return NotificationDltMessageDocument(
            id = UUID.nameUUIDFromBytes("notification.dispatch:0:100".toByteArray()).toString(),
            originalTopic = "notification.dispatch",
            originalPartition = 0,
            originalOffset = 100,
            originalTimestamp = NOW.minusSeconds(1),
            dltTopic = "notification.dispatch.dlt",
            dltPartition = 0,
            dltOffset = 200,
            consumerGroup = "notification-worker",
            messageKey = "key-1",
            payload = """{"notificationId":"n1"}""",
            exceptionFqcn = "java.net.SocketTimeoutException",
            exceptionMessage = "timeout",
            deadLetteredAt = NOW,
            storedAt = NOW.plusSeconds(1),
            discardedAt = NOW.plusSeconds(10),
            discardReason = "operator discard",
            reprocessedAt = null,
            reprocessReason = null,
            status = NotificationDltMessageStatus.DISCARDED.name,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-13T00:00:00Z")
    }
}
