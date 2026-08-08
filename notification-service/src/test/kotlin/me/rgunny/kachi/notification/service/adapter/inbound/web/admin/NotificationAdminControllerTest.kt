package me.rgunny.kachi.notification.service.adapter.inbound.web.admin

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.inbound.admin.model.DeadNotificationQuery
import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationAdminResult
import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationHistoryQuery
import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationHistoryResult
import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationHistorySummary
import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationSummary
import me.rgunny.kachi.notification.application.port.inbound.admin.model.RecoverDeadNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.admin.model.RecoverDeadNotificationResult
import me.rgunny.kachi.notification.application.port.inbound.admin.NotificationAdminUseCase
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationHistoryId
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutboxId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.request.RecoverDeadNotificationRequest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("NotificationAdminController")
class NotificationAdminControllerTest {

    private val useCase = CapturingNotificationAdminUseCase()
    private val controller = NotificationAdminController(useCase)

    @Test
    @DisplayName("DEAD notification 목록을 조회한다")
    fun findDead() = runBlocking {
        val response = controller.findDead(limit = 20)

        assertEquals(20, useCase.lastDeadQuery?.batchSize)
        val body = assertNotNull(response.body)
        val data = assertNotNull(body.data)
        assertEquals(true, body.success)
        assertEquals(1, data.notifications.size)
        assertEquals(NotificationStatus.DEAD.name, data.notifications.single().status)
    }

    @Test
    @DisplayName("notification 상태 전이 history를 조회한다")
    fun findHistories() = runBlocking {
        val response = controller.findHistories(useCase.notificationId.id, limit = 30)

        assertEquals(useCase.notificationId, useCase.lastHistoryQuery?.notificationId)
        assertEquals(30, useCase.lastHistoryQuery?.batchSize)
        val body = assertNotNull(response.body)
        val data = assertNotNull(body.data)
        assertEquals(1, data.histories.size)
        assertEquals(NotificationStatus.FAILED.name, data.histories.single().fromStatus)
        assertEquals(NotificationStatus.DEAD.name, data.histories.single().toStatus)
    }

    @Test
    @DisplayName("DEAD notification 복구 요청을 use case로 전달한다")
    fun recoverDead() = runBlocking {
        val response = controller.recoverDead(
            notificationId = useCase.notificationId.id,
            request = RecoverDeadNotificationRequest("operator retry"),
        )

        assertEquals(useCase.notificationId, useCase.lastRecoverCommand?.notificationId)
        assertEquals("operator retry", useCase.lastRecoverCommand?.reason)
        val body = assertNotNull(response.body)
        val data = assertNotNull(body.data)
        assertEquals(NotificationStatus.REQUESTED.name, data.status)
    }

    private class CapturingNotificationAdminUseCase : NotificationAdminUseCase {
        val now: Instant = Instant.parse("2026-07-13T00:00:00Z")
        val notificationId: NotificationId = NotificationId.newId()
        var lastDeadQuery: DeadNotificationQuery? = null
            private set
        var lastHistoryQuery: NotificationHistoryQuery? = null
            private set
        var lastRecoverCommand: RecoverDeadNotificationCommand? = null
            private set

        override suspend fun findDead(query: DeadNotificationQuery): NotificationAdminResult {
            lastDeadQuery = query
            return NotificationAdminResult(listOf(notificationSummary(NotificationStatus.DEAD)))
        }

        override suspend fun findHistories(query: NotificationHistoryQuery): NotificationHistoryResult {
            lastHistoryQuery = query
            return NotificationHistoryResult(
                histories = listOf(
                    NotificationHistorySummary(
                        historyId = NotificationHistoryId.newId(),
                        notificationId = notificationId,
                        fromStatus = NotificationStatus.FAILED,
                        toStatus = NotificationStatus.DEAD,
                        reason = "retry exhausted",
                        createdAt = now,
                    )
                )
            )
        }

        override suspend fun recoverDead(command: RecoverDeadNotificationCommand): RecoverDeadNotificationResult {
            lastRecoverCommand = command
            return RecoverDeadNotificationResult(
                notificationId = notificationId,
                status = NotificationStatus.REQUESTED,
                outboxId = NotificationOutboxId.newId(),
                recoveredAt = now,
            )
        }

        private fun notificationSummary(status: NotificationStatus): NotificationSummary {
            return NotificationSummary(
                notificationId = notificationId,
                requestId = "request-1",
                requester = "collector-service",
                channel = NotificationChannel.SLACK,
                recipient = "C123",
                status = status,
                failureReason = "invalid recipient",
                dispatchAttempts = 3,
                requestedAt = now.minusSeconds(300),
                updatedAt = now,
                lastTransitionAt = now,
            )
        }
    }
}
