package me.rgunny.kachi.notification.service.adapter.inbound.web.admin

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.dto.outbox.DeadNotificationOutboxQuery
import me.rgunny.kachi.notification.application.port.dto.outbox.NotificationOutboxAdminResult
import me.rgunny.kachi.notification.application.port.dto.outbox.NotificationOutboxSummary
import me.rgunny.kachi.notification.application.port.dto.outbox.RecoverNotificationOutboxCommand
import me.rgunny.kachi.notification.application.port.dto.outbox.RecoverNotificationOutboxResult
import me.rgunny.kachi.notification.application.port.inbound.NotificationOutboxAdminUseCase
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutboxId
import me.rgunny.kachi.notification.domain.NotificationOutboxStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("NotificationOutboxAdminController")
class NotificationOutboxAdminControllerTest {

    private val useCase = CapturingNotificationOutboxAdminUseCase()
    private val controller = NotificationOutboxAdminController(useCase)

    @Test
    @DisplayName("DEAD outbox 목록을 조회한다")
    fun findDead() = runBlocking {
        val response = controller.findDead(limit = 20)

        assertEquals(20, useCase.lastQuery?.batchSize)
        val body = assertNotNull(response.body)
        val data = assertNotNull(body.data)
        assertEquals(true, body.success)
        assertEquals(1, data.outboxes.size)
        assertEquals(NotificationOutboxStatus.DEAD.name, data.outboxes.first().status)
    }

    @Test
    @DisplayName("outbox 복구 요청을 use case로 전달한다")
    fun recover() = runBlocking {
        val outboxId = useCase.outboxId.id

        val response = controller.recover(outboxId)

        assertEquals(outboxId, useCase.lastRecoverCommand?.outboxId?.id)
        val body = assertNotNull(response.body)
        val data = assertNotNull(body.data)
        assertEquals(NotificationOutboxStatus.PENDING.name, data.outbox.status)
        assertEquals(useCase.now, data.recoveredAt)
    }

    private class CapturingNotificationOutboxAdminUseCase : NotificationOutboxAdminUseCase {
        val now: Instant = Instant.parse("2026-06-20T00:00:00Z")
        val outboxId: NotificationOutboxId = NotificationOutboxId.newId()
        val notificationId: NotificationId = NotificationId.newId()
        var lastQuery: DeadNotificationOutboxQuery? = null
            private set
        var lastRecoverCommand: RecoverNotificationOutboxCommand? = null
            private set

        override suspend fun findDead(query: DeadNotificationOutboxQuery): NotificationOutboxAdminResult {
            lastQuery = query
            return NotificationOutboxAdminResult(
                outboxes = listOf(summary(NotificationOutboxStatus.DEAD))
            )
        }

        override suspend fun recover(
            command: RecoverNotificationOutboxCommand
        ): RecoverNotificationOutboxResult {
            lastRecoverCommand = command
            return RecoverNotificationOutboxResult(
                outbox = summary(NotificationOutboxStatus.PENDING),
                recoveredAt = now,
            )
        }

        private fun summary(status: NotificationOutboxStatus): NotificationOutboxSummary {
            return NotificationOutboxSummary(
                outboxId = outboxId,
                notificationId = notificationId,
                topic = "notification.dispatch",
                partitionKey = "C123",
                status = status,
                retryCount = 0,
                nextRetryAt = now,
                lastError = null,
                createdAt = now.minusSeconds(60),
                publishedAt = null,
            )
        }
    }
}
