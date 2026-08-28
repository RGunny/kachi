package me.rgunny.kachi.notification.routing.application.service

import me.rgunny.kachi.notification.contract.NotificationChannel
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteAdminCommand
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteNotificationOutcome
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteSummaryCommand
import me.rgunny.kachi.notification.routing.application.port.outbound.messaging.model.NotificationRequestOrigin
import me.rgunny.kachi.notification.routing.application.port.outbound.subscriber.model.Subscriber
import me.rgunny.kachi.notification.routing.domain.RoutingJob
import me.rgunny.kachi.notification.routing.domain.RoutingJobKind
import me.rgunny.kachi.notification.routing.domain.RoutingJobStatus
import me.rgunny.kachi.notification.routing.exception.routing.RoutingErrorCode
import me.rgunny.kachi.notification.routing.exception.routing.SubscriberReaderException
import me.rgunny.kachi.notification.routing.fake.FakeNotificationRequestPublisherPort
import me.rgunny.kachi.notification.routing.fake.FakeRoutingJobPersistencePort
import me.rgunny.kachi.notification.routing.fake.FakeSubscriberReaderPort
import me.rgunny.kachi.notification.routing.support.RoutingTestFixture.CLOCK
import me.rgunny.kachi.notification.routing.support.RoutingTestFixture.NOW
import me.rgunny.kachi.notification.routing.support.runSuspend
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
    @DisplayName("구독자 x 채널마다 결정적 requestId로 발행한다")
    fun routeSummary() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort()
        val reader = FakeSubscriberReaderPort(subscribers)
        val publisher = FakeNotificationRequestPublisherPort()
        val service = service(jobs, reader, publisher)

        val result = service.routeSummary(summaryCommand())

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(3, result.targetCount)
        assertEquals(3, result.publishedCount)
        assertEquals(listOf("tesla"), reader.requestedKeywords)
        assertEquals(
            listOf("sum:summary-1:u:user-1:c:SLACK", "sum:summary-1:u:user-1:c:TELEGRAM", "sum:summary-1:u:user-2:c:DISCORD"),
            publisher.published.map { it.requestId },
        )
        val first = publisher.published.first()
        assertEquals("notification-routing", first.requester)
        assertEquals(NotificationChannel.SLACK, first.channel)
        assertEquals("ref-1", first.recipientRef)
        assertEquals("summary body", first.message)
        assertEquals(NotificationRequestOrigin("summary-1", "tesla", "user-1"), first.origin)

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
            it.put(RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", now.minusSeconds(60)).complete(3, 3, now.minusSeconds(30)))
        }
        val reader = FakeSubscriberReaderPort(subscribers)
        val publisher = FakeNotificationRequestPublisherPort()

        val result = service(jobs, reader, publisher).routeSummary(summaryCommand())

        assertEquals(RouteNotificationOutcome.SKIPPED, result.outcome)
        assertEquals(3, result.targetCount)
        assertTrue(publisher.published.isEmpty())
        assertTrue(reader.requestedKeywords.isEmpty())
        assertTrue(jobs.saved.isEmpty())
    }

    @Test
    @DisplayName("STARTED job은 새로 만들지 않고 전체를 다시 발행한다")
    fun resumeStarted() = runSuspend {
        val started = RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", now.minusSeconds(60))
        val jobs = FakeRoutingJobPersistencePort().also { it.put(started) }
        val publisher = FakeNotificationRequestPublisherPort()

        val result = service(jobs, FakeSubscriberReaderPort(subscribers), publisher).routeSummary(summaryCommand())

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(3, result.targetCount)
        assertEquals(3, result.publishedCount)
        assertEquals(3, publisher.published.size)
        assertTrue(jobs.inserted.isEmpty())
        assertEquals(started.id, jobs.saved.single().id)
    }

    @Test
    @DisplayName("구독자가 없으면 발행 없이 완료한다")
    fun noSubscribers() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort()
        val publisher = FakeNotificationRequestPublisherPort()

        val result = service(jobs, FakeSubscriberReaderPort(), publisher).routeSummary(summaryCommand())

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(0, result.targetCount)
        assertTrue(publisher.published.isEmpty())
        assertEquals(RoutingJobStatus.COMPLETED, jobs.saved.single().status)
    }

    @Test
    @DisplayName("발행 중 예외는 전파하고 job은 STARTED로 남는다")
    fun propagatePublishFailure() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort()
        val publisher = FakeNotificationRequestPublisherPort().also { it.failAt = 2 }

        assertFailsWith<IllegalStateException> {
            service(jobs, FakeSubscriberReaderPort(subscribers), publisher).routeSummary(summaryCommand())
        }

        assertEquals(2, publisher.published.size)
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
        val publisher = FakeNotificationRequestPublisherPort()

        assertFailsWith<SubscriberReaderException> {
            service(jobs, reader, publisher).routeSummary(summaryCommand())
        }

        assertTrue(publisher.published.isEmpty())
        assertEquals(RoutingJobStatus.STARTED, jobs.findByEventKey("summary-1")!!.status)
    }

    @Test
    @DisplayName("insert 충돌이면 재조회한 job으로 진행한다")
    fun resolveInsertConflict() = runSuspend {
        val existing = RoutingJob.start("summary-1", RoutingJobKind.SUMMARY, "tesla", now.minusSeconds(1))
        val jobs = FakeRoutingJobPersistencePort().also { it.conflictOnce = existing }
        val publisher = FakeNotificationRequestPublisherPort()

        val result = service(jobs, FakeSubscriberReaderPort(subscribers), publisher).routeSummary(summaryCommand())

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(existing.id, result.jobId)
        assertTrue(jobs.inserted.isEmpty())
        assertEquals(3, publisher.published.size)
    }

    @Test
    @DisplayName("관리자 알림은 설정 수신처 채널마다 1건 발행한다")
    fun routeAdmin() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort()
        val reader = FakeSubscriberReaderPort(subscribers)
        val publisher = FakeNotificationRequestPublisherPort()

        val result = service(jobs, reader, publisher).routeAdmin(
            RouteAdminCommand(eventKey = "quarantine-1:1700000000000", keyword = "tesla", message = "admin body")
        )

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(3, result.targetCount)
        assertEquals(3, result.publishedCount)
        assertTrue(reader.requestedKeywords.isEmpty())
        assertEquals(
            setOf(
                "adm:quarantine-1:1700000000000:c:SLACK",
                "adm:quarantine-1:1700000000000:c:DISCORD",
                "adm:quarantine-1:1700000000000:c:TELEGRAM",
            ),
            publisher.published.map { it.requestId }.toSet(),
        )
        publisher.published.forEach {
            assertEquals("admin", it.recipientRef)
            assertEquals("admin body", it.message)
            assertEquals(NotificationRequestOrigin(summaryId = null, keyword = "tesla", userId = null), it.origin)
        }
        val job = jobs.saved.single()
        assertEquals(RoutingJobKind.ADMIN, job.kind)
        assertEquals("quarantine-1:1700000000000", job.eventKey)
    }

    @Test
    @DisplayName("관리자 수신처가 비어 있으면 대상 0으로 완료한다")
    fun routeAdminWithoutRecipients() = runSuspend {
        val jobs = FakeRoutingJobPersistencePort()
        val publisher = FakeNotificationRequestPublisherPort()
        val service = service(jobs, FakeSubscriberReaderPort(), publisher, adminRecipients = emptyMap())

        val result = service.routeAdmin(RouteAdminCommand("quarantine-1:1", "tesla", "admin body"))

        assertEquals(RouteNotificationOutcome.ROUTED, result.outcome)
        assertEquals(0, result.targetCount)
        assertTrue(publisher.published.isEmpty())
        assertEquals(RoutingJobStatus.COMPLETED, jobs.saved.single().status)
    }

    private fun service(
        jobs: FakeRoutingJobPersistencePort,
        reader: FakeSubscriberReaderPort,
        publisher: FakeNotificationRequestPublisherPort,
        adminRecipients: Map<NotificationChannel, String> = this.adminRecipients,
    ): RouteNotificationService {
        return RouteNotificationService(
            routingJobPersistencePort = jobs,
            subscriberReaderPort = reader,
            notificationRequestPublisherPort = publisher,
            policy = RoutingPolicy(requester = "notification-routing", adminRecipientRefs = adminRecipients),
            clock = clock,
        )
    }

    private fun summaryCommand(): RouteSummaryCommand {
        return RouteSummaryCommand(summaryId = "summary-1", keyword = "tesla", message = "summary body")
    }
}
