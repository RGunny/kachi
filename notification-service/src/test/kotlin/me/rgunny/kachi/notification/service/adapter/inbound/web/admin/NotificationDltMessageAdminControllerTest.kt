package me.rgunny.kachi.notification.service.adapter.inbound.web.admin

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.DiscardNotificationDltMessageCommand
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.DiscardNotificationDltMessageResult
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageDetail
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageAdminResult
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageQuery
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageSummary
import me.rgunny.kachi.notification.application.port.inbound.dlt.NotificationDltMessageAdminUseCase
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.request.DiscardNotificationDltMessageRequest
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

    @Test
    @DisplayName("DLT 메시지 상세를 조회한다")
    fun get() = runBlocking {
        val response = controller.get(useCase.messageId.id)

        assertEquals(useCase.messageId, useCase.lastMessageId)
        val body = assertNotNull(response.body)
        val data = assertNotNull(body.data)
        assertEquals(NotificationDltMessageStatus.PENDING.name, data.status)
        assertEquals("""{"notificationId":"n1"}""", data.payload)
    }

    @Test
    @DisplayName("DLT 메시지 폐기 요청을 use case로 전달한다")
    fun discard() = runBlocking {
        val response = controller.discard(
            messageId = useCase.messageId.id,
            request = DiscardNotificationDltMessageRequest("operator discard"),
        )

        assertEquals(useCase.messageId, useCase.lastDiscardCommand?.messageId)
        assertEquals("operator discard", useCase.lastDiscardCommand?.reason)
        val body = assertNotNull(response.body)
        val data = assertNotNull(body.data)
        assertEquals(NotificationDltMessageStatus.DISCARDED.name, data.status)
        assertEquals("operator discard", data.discardReason)
    }

    private class CapturingNotificationDltMessageAdminUseCase : NotificationDltMessageAdminUseCase {
        val now: Instant = Instant.parse("2026-07-13T00:00:00Z")
        val messageId: NotificationDltMessageId = NotificationDltMessageId.fromOriginalRecord("notification.dispatch", 0, 100)
        var lastQuery: NotificationDltMessageQuery? = null
            private set
        var lastMessageId: NotificationDltMessageId? = null
            private set
        var lastDiscardCommand: DiscardNotificationDltMessageCommand? = null
            private set

        override suspend fun find(query: NotificationDltMessageQuery): NotificationDltMessageAdminResult {
            lastQuery = query
            return NotificationDltMessageAdminResult(
                messages = listOf(
                    NotificationDltMessageSummary(
                        messageId = messageId,
                        status = NotificationDltMessageStatus.PENDING,
                        originalTopic = "notification.dispatch",
                        originalPartition = 0,
                        originalOffset = 100,
                        originalTimestamp = now.minusSeconds(1),
                        dltTopic = "notification.dispatch.dlt",
                        dltPartition = 0,
                        dltOffset = 200,
                        consumerGroup = "notification-worker",
                        messageKey = "key-1",
                        exceptionFqcn = "java.net.SocketTimeoutException",
                        exceptionMessage = "timeout",
                        deadLetteredAt = now,
                        storedAt = now.plusSeconds(1),
                        discardedAt = null,
                        discardReason = null,
                        reprocessedAt = null,
                        reprocessReason = null,
                    )
                )
            )
        }

        override suspend fun get(messageId: NotificationDltMessageId): NotificationDltMessageDetail {
            lastMessageId = messageId
            return NotificationDltMessageDetail(
                messageId = messageId,
                status = NotificationDltMessageStatus.PENDING,
                originalTopic = "notification.dispatch",
                originalPartition = 0,
                originalOffset = 100,
                originalTimestamp = now.minusSeconds(1),
                dltTopic = "notification.dispatch.dlt",
                dltPartition = 0,
                dltOffset = 200,
                consumerGroup = "notification-worker",
                messageKey = "key-1",
                payload = """{"notificationId":"n1"}""",
                exceptionFqcn = "java.net.SocketTimeoutException",
                exceptionMessage = "timeout",
                deadLetteredAt = now,
                storedAt = now.plusSeconds(1),
                discardedAt = null,
                discardReason = null,
                reprocessedAt = null,
                reprocessReason = null,
            )
        }

        override suspend fun discard(
            command: DiscardNotificationDltMessageCommand
        ): DiscardNotificationDltMessageResult {
            lastDiscardCommand = command
            return DiscardNotificationDltMessageResult(
                messageId = command.messageId,
                status = NotificationDltMessageStatus.DISCARDED,
                discardedAt = now,
                discardReason = command.reason,
            )
        }
    }
}
