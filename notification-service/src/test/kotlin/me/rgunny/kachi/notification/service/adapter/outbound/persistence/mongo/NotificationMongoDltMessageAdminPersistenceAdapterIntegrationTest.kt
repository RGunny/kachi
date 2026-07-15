package me.rgunny.kachi.notification.service.adapter.outbound.persistence.mongo

import kotlinx.coroutines.runBlocking
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
        val older = document(originalOffset = 100, failedAt = NOW.minusSeconds(60))
        val newer = document(originalOffset = 101, failedAt = NOW)
        val discarded = document(
            originalOffset = 102,
            failedAt = NOW.plusSeconds(60),
            status = NotificationDltMessageStatus.DISCARDED,
        )
        mongoTemplate.insertAll(listOf(older, newer, discarded)).collectList().block()

        val result = adapter.findByStatus(NotificationDltMessageStatus.PENDING, batchSize = 10)

        assertEquals(listOf(101L, 100L), result.map { it.originalOffset })
        assertEquals(listOf(NotificationDltMessageStatus.PENDING, NotificationDltMessageStatus.PENDING), result.map { it.status })
    }

    private fun document(
        originalOffset: Long,
        failedAt: Instant,
        status: NotificationDltMessageStatus = NotificationDltMessageStatus.PENDING,
    ): NotificationDltMessageDocument {
        return NotificationDltMessageDocument(
            id = UUID.nameUUIDFromBytes("notification.dispatch:0:$originalOffset".toByteArray()).toString(),
            originalTopic = "notification.dispatch",
            originalPartition = 0,
            originalOffset = originalOffset,
            dltTopic = "notification.dispatch.dlt",
            dltPartition = 0,
            dltOffset = originalOffset + 100,
            consumerGroup = "notification-worker",
            messageKey = "key-$originalOffset",
            payload = """{"notificationId":"n$originalOffset"}""",
            exceptionFqcn = "java.net.SocketTimeoutException",
            exceptionMessage = "timeout",
            failedAt = failedAt,
            receivedAt = failedAt.plusSeconds(1),
            status = status.name,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-13T00:00:00Z")
    }
}
