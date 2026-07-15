package me.rgunny.kachi.notification.service.adapter.inbound.web.admin

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageAdminResult
import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageQuery
import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageSummary
import me.rgunny.kachi.notification.application.port.inbound.NotificationDltMessageAdminUseCase
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("NotificationDltMessageAdminController")
class NotificationDltMessageAdminControllerTest {

    private val useCase = CapturingNotificationDltMessageAdminUseCase()
    private val controller = NotificationDltMessageAdminController(useCase)

    @Test
    @DisplayName("DLT 메시지 목록을 조회한다")
    fun find() = runBlocking {
        val response = controller.find(
            status = NotificationDltMessageStatus.PENDING,
            limit = 20,
        )

        assertEquals(NotificationDltMessageStatus.PENDING, useCase.lastQuery?.status)
        assertEquals(20, useCase.lastQuery?.batchSize)
        val body = assertNotNull(response.body)
        val data = assertNotNull(body.data)
        assertEquals(true, body.success)
        assertEquals(1, data.messages.size)
        assertEquals(NotificationDltMessageStatus.PENDING.name, data.messages.single().status)
        assertEquals("java.net.SocketTimeoutException", data.messages.single().exceptionFqcn)
    }

    private class CapturingNotificationDltMessageAdminUseCase : NotificationDltMessageAdminUseCase {
        val now: Instant = Instant.parse("2026-07-13T00:00:00Z")
        var lastQuery: NotificationDltMessageQuery? = null
            private set

        override suspend fun find(query: NotificationDltMessageQuery): NotificationDltMessageAdminResult {
            lastQuery = query
            return NotificationDltMessageAdminResult(
                messages = listOf(
                    NotificationDltMessageSummary(
                        messageId = NotificationDltMessageId.fromOriginalRecord("notification.dispatch", 0, 100),
                        status = NotificationDltMessageStatus.PENDING,
                        originalTopic = "notification.dispatch",
                        originalPartition = 0,
                        originalOffset = 100,
                        dltTopic = "notification.dispatch.dlt",
                        dltPartition = 0,
                        dltOffset = 200,
                        consumerGroup = "notification-worker",
                        messageKey = "key-1",
                        exceptionFqcn = "java.net.SocketTimeoutException",
                        exceptionMessage = "timeout",
                        failedAt = now,
                        receivedAt = now.plusSeconds(1),
                    )
                )
            )
        }
    }
}
