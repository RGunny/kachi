package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mongo

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.worker.adapter.outbound.persistence.document.NotificationDltMessageDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant
import kotlin.test.assertEquals

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
    @DisplayName("같은 원본 record 위치의 DLT 메시지는 upsert로 중복 저장하지 않는다")
    fun saveIdempotently() = runBlocking {
        adapter.save(message())
        adapter.save(message(dltOffset = 201))

        val documents = mongoTemplate.findAll(NotificationDltMessageDocument::class.java)
            .collectList()
            .block()
            .orEmpty()

        assertEquals(1, documents.size)
        assertEquals(201, documents.single().dltOffset)
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
            dltTopic = "notification.dispatch.dlt",
            dltPartition = 0,
            dltOffset = dltOffset,
            consumerGroup = "notification-worker",
            messageKey = "key-1",
            payload = """{"notificationId":"n1"}""",
            exceptionFqcn = "java.net.SocketTimeoutException",
            exceptionMessage = "timeout",
            failedAt = Instant.parse("2026-07-13T00:00:00Z"),
            receivedAt = Instant.parse("2026-07-13T00:00:01Z"),
        )
    }
}
