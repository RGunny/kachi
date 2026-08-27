package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.inbound.routing.model.RouteAdminCommand
import me.rgunny.kachi.notification.application.port.inbound.routing.model.RouteNotificationOutcome
import me.rgunny.kachi.notification.application.port.inbound.routing.model.RouteSummaryCommand
import me.rgunny.kachi.notification.application.port.outbound.routing.model.Subscriber
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationOrigin
import me.rgunny.kachi.notification.domain.RoutingJob
import me.rgunny.kachi.notification.domain.RoutingJobKind
import me.rgunny.kachi.notification.domain.RoutingJobStatus
import me.rgunny.kachi.notification.exception.routing.RoutingErrorCode
import me.rgunny.kachi.notification.exception.routing.SubscriberReaderException
import me.rgunny.kachi.notification.fake.FakeRequestNotificationUseCase
import me.rgunny.kachi.notification.fake.FakeRoutingJobPersistencePort
import me.rgunny.kachi.notification.fake.FakeSubscriberReaderPort
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.CLOCK
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("RouteNotificationService")
class RouteNotificationServiceTest {
    private val now = NOW
    private val clock = CLOCK

    private val subscribers = listOf(
        Subscriber("user-1", NotificationChannel.SLACK, "ref-1"),
        Subscriber("user-1", NotificationChannel.TELEGRAM, "ref-2"),
        Subscriber("user-2", NotificationChannel.DISCORD, "ref-3"),
    )
    private val adminRecipients = mapOf(
        NotificationChannel.SLACK to "admin",
        NotificationChannel.DISCORD to "admin",
        NotificationChannel.TELEGRAM to "admin",
    )

    @Test
    @DisplayName("구독자 x 채널마다 결정적 requestId로 접수한다")
    fun routeSummary() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort()
        val reader = FakeSubscriberReaderPort(subscribers)
        val request = FakeRequestNotificationUseCase(now)
        val service = service(jobs, reader, request)

        val result = service.routeSummary(summaryCommand())

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(3, result.targetCount)
        assertEquals(3, result.acceptedCount)
        assertEquals(0, result.duplicatedCount)
        assertEquals(listOf("tesla"), reader.requestedKeywords)
        assertEquals(
            listOf("sum:summary-1:u:user-1:c:SLACK", "sum:summary-1:u:user-1:c:TELEGRAM", "sum:summary-1:u:user-2:c:DISCORD"),
            request.commands.map { it.requestId },
        )
        val first = request.commands.first()
        assertEquals("notification-routing", first.requester)
        assertEquals(NotificationChannel.SLACK, first.channel)
        assertEquals("ref-1", first.recipient)
        assertEquals("summary body", first.message)
        assertEquals(NotificationOrigin("summary-1", "tesla", "user-1"), first.origin)

        val job = jobs.saved.single()
        assertEquals(RoutingJobStatus.COMPLETED, job.status)
        assertEquals(RoutingJobKind.SUMMARY, job.kind)
        assertEquals("summary-1", job.eventKey)
        assertEquals(now, job.completedAt)
        assertEquals(job.id, result.jobId)
    }

    @Test
    @DisplayName("COMPLETED job은 다시 라우팅하지 않는다")
    fun skipCompleted() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort().also {
            it.put(RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", now.minusSeconds(60)).complete(3, 3, 0, now.minusSeconds(30)))
        }
        val reader = FakeSubscriberReaderPort(subscribers)
        val request = FakeRequestNotificationUseCase(now)

        val result = service(jobs, reader, request).routeSummary(summaryCommand())

        assertEquals(RouteNotificationOutcome.SKIPPED, result.outcome)
        assertEquals(3, result.targetCount)
        assertTrue(request.commands.isEmpty())
        assertTrue(reader.requestedKeywords.isEmpty())
        assertTrue(jobs.saved.isEmpty())
    }

    @Test
    @DisplayName("STARTED job은 이어서 진행하고 중복 접수를 집계한다")
    fun resumeStarted() = runSuspend {
        val started = RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", now.minusSeconds(60))
        val jobs = FakeRoutingJobPersistencePort().also { it.put(started) }
        val request = FakeRequestNotificationUseCase(now).also {
            it.duplicatedRequestIds += "sum:summary-1:u:user-1:c:SLACK"
        }

        val result = service(jobs, FakeSubscriberReaderPort(subscribers), request).routeSummary(summaryCommand())

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(3, result.targetCount)
        assertEquals(2, result.acceptedCount)
        assertEquals(1, result.duplicatedCount)
        assertTrue(jobs.inserted.isEmpty())
        assertEquals(started.id, jobs.saved.single().id)
    }

    @Test
    @DisplayName("구독자가 없으면 알림 없이 완료한다")
    fun noSubscribers() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort()
        val request = FakeRequestNotificationUseCase(now)

        val result = service(jobs, FakeSubscriberReaderPort(), request).routeSummary(summaryCommand())

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(0, result.targetCount)
        assertTrue(request.commands.isEmpty())
        assertEquals(RoutingJobStatus.COMPLETED, jobs.saved.single().status)
    }

    @Test
    @DisplayName("접수 중 예외는 전파하고 job은 STARTED로 남는다")
    fun propagateRequestFailure() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort()
        val request = FakeRequestNotificationUseCase(now).also { it.failAt = 1 }

        assertFailsWith<IllegalStateException> {
            service(jobs, FakeSubscriberReaderPort(subscribers), request).routeSummary(summaryCommand())
        }

        assertEquals(2, request.commands.size)
        assertTrue(jobs.saved.isEmpty())
        assertEquals(RoutingJobStatus.STARTED, jobs.findByEventKey("summary-1")!!.status)
    }

    @Test
    @DisplayName("구독 조회 실패는 전파한다")
    fun propagateReaderFailure() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort()
        val reader = FakeSubscriberReaderPort().also {
            it.failure = SubscriberReaderException(RoutingErrorCode.USER_SERVICE_REQUEST_FAILED, "status=503")
        }
        val request = FakeRequestNotificationUseCase(now)

        assertFailsWith<SubscriberReaderException> {
            service(jobs, reader, request).routeSummary(summaryCommand())
        }

        assertTrue(request.commands.isEmpty())
        assertEquals(RoutingJobStatus.STARTED, jobs.findByEventKey("summary-1")!!.status)
    }

    @Test
    @DisplayName("insert 충돌이면 재조회한 job으로 진행한다")
    fun resolveInsertConflict() = runSuspend {
        val existing = RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", now.minusSeconds(1))
        val jobs = FakeRoutingJobPersistencePort().also { it.conflictOnce = existing }
        val request = FakeRequestNotificationUseCase(now)

        val result = service(jobs, FakeSubscriberReaderPort(subscribers), request).routeSummary(summaryCommand())

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(existing.id, result.jobId)
        assertTrue(jobs.inserted.isEmpty())
        assertEquals(3, request.commands.size)
    }

    @Test
    @DisplayName("관리자 알림은 설정 수신처 채널마다 1건 접수한다")
    fun routeAdmin() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort()
        val reader = FakeSubscriberReaderPort(subscribers)
        val request = FakeRequestNotificationUseCase(now)

        val result = service(jobs, reader, request).routeAdmin(
            RouteAdminCommand(eventKey = "quarantine-1:1700000000000", keyword = "tesla", message = "admin body")
        )

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(3, result.targetCount)
        assertEquals(3, result.acceptedCount)
        assertTrue(reader.requestedKeywords.isEmpty())
        assertEquals(
            setOf(
                "adm:quarantine-1:1700000000000:c:SLACK",
                "adm:quarantine-1:1700000000000:c:DISCORD",
                "adm:quarantine-1:1700000000000:c:TELEGRAM",
            ),
            request.commands.map { it.requestId }.toSet(),
        )
        request.commands.forEach {
            assertEquals("admin", it.recipient)
            assertEquals("admin body", it.message)
            assertEquals(NotificationOrigin(summaryId = null, keyword = "tesla", userId = null), it.origin)
        }
        val job = jobs.saved.single()
        assertEquals(RoutingJobKind.ADMIN, job.kind)
        assertEquals("quarantine-1:1700000000000", job.eventKey)
    }

    @Test
    @DisplayName("관리자 수신처가 비어 있으면 대상 0으로 완료한다")
    fun routeAdminWithoutRecipients() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort()
        val request = FakeRequestNotificationUseCase(now)
        val service = service(jobs, FakeSubscriberReaderPort(), request, adminRecipients = emptyMap())

        val result = service.routeAdmin(RouteAdminCommand("quarantine-1:1", "tesla", "admin body"))

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(0, result.targetCount)
        assertTrue(request.commands.isEmpty())
        assertEquals(RoutingJobStatus.COMPLETED, jobs.saved.single().status)
    }

    private fun service(
        jobs: FakeRoutingJobPersistencePort,
        reader: FakeSubscriberReaderPort,
        request: FakeRequestNotificationUseCase,
        adminRecipients: Map<NotificationChannel, String> = this.adminRecipients,
    ): RouteNotificationService {
        return RouteNotificationService(
            routingJobPersistencePort = jobs,
            subscriberReaderPort = reader,
            requestNotificationUseCase = request,
            policy = RoutingPolicy(requester = "notification-routing", adminRecipients = adminRecipients),
            clock = clock,
        )
    }

    private fun summaryCommand(): RouteSummaryCommand {
        return RouteSummaryCommand(summaryId = "summary-1", keyword = "tesla", message = "summary body")
    }
}
