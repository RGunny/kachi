package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.inbound.request.RequestNotificationUseCase
import me.rgunny.kachi.notification.application.port.inbound.request.model.RequestNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.routing.RouteAdminNotificationUseCase
import me.rgunny.kachi.notification.application.port.inbound.routing.RouteSummaryNotificationUseCase
import me.rgunny.kachi.notification.application.port.inbound.routing.model.RouteAdminCommand
import me.rgunny.kachi.notification.application.port.inbound.routing.model.RouteNotificationOutcome
import me.rgunny.kachi.notification.application.port.inbound.routing.model.RouteNotificationResult
import me.rgunny.kachi.notification.application.port.inbound.routing.model.RouteSummaryCommand
import me.rgunny.kachi.notification.application.port.outbound.persistence.RoutingJobPersistencePort
import me.rgunny.kachi.notification.application.port.outbound.routing.SubscriberReaderPort
import me.rgunny.kachi.notification.domain.NotificationOrigin
import me.rgunny.kachi.notification.domain.RoutingJob
import me.rgunny.kachi.notification.domain.RoutingJobKind
import me.rgunny.kachi.notification.domain.RoutingJobStatus
import me.rgunny.kachi.notification.exception.routing.RoutingJobConflictException
import java.time.Clock

/**
 * 이벤트 1건을 대상마다 알림 접수로 라우팅한다.
 *
 * 예외를 잡지 않는다. 대상 중 하나라도 접수에 실패하면 그대로 전파되어 job은 STARTED로 남고,
 * 같은 이벤트가 다시 오면 이어서 진행한다. 이미 접수된 대상은 requestId 멱등으로 duplicated가 된다.
 */
class RouteNotificationService(
    private val routingJobPersistencePort: RoutingJobPersistencePort,
    private val subscriberReaderPort: SubscriberReaderPort,
    private val requestNotificationUseCase: RequestNotificationUseCase,
    private val policy: RoutingPolicy,
    private val clock: Clock,
) : RouteSummaryNotificationUseCase, RouteAdminNotificationUseCase {

    override suspend fun routeSummary(command: RouteSummaryCommand): RouteNotificationResult {
        return route(command.summaryId, RoutingJobKind.SUMMARY, command.keyword) { job ->
            subscriberReaderPort.findSubscribers(command.keyword).map { subscriber ->
                RequestNotificationCommand(
                    requestId = RoutingRequestId.forSummary(command.summaryId, subscriber.userId, subscriber.channel),
                    requester = policy.requester,
                    channel = subscriber.channel,
                    recipient = subscriber.recipientRef,
                    message = command.message,
                    origin = NotificationOrigin(
                        summaryId = command.summaryId,
                        keyword = job.keyword,
                        userId = subscriber.userId,
                    ),
                )
            }
        }
    }

    override suspend fun routeAdmin(command: RouteAdminCommand): RouteNotificationResult {
        return route(command.eventKey, RoutingJobKind.ADMIN, command.keyword) { job ->
            policy.adminRecipients.map { (channel, recipient) ->
                RequestNotificationCommand(
                    requestId = RoutingRequestId.forAdmin(command.eventKey, channel),
                    requester = policy.requester,
                    channel = channel,
                    recipient = recipient,
                    message = command.message,
                    origin = NotificationOrigin(summaryId = null, keyword = job.keyword, userId = null),
                )
            }
        }
    }

    private suspend fun route(
        eventKey: String,
        kind: RoutingJobKind,
        keyword: String,
        targets: suspend (RoutingJob) -> List<RequestNotificationCommand>,
    ): RouteNotificationResult {
        // 1. eventKey당 job 하나. COMPLETED면 다시 라우팅하지 않는다.
        val job = findOrStart(eventKey, kind, keyword)
        if (job.status == RoutingJobStatus.COMPLETED) {
            return result(job, RouteNotificationOutcome.SKIPPED)
        }

        // 2. 대상마다 순차 접수. 실패는 전파한다.
        val commands = targets(job)
        var accepted = 0
        var duplicated = 0
        commands.forEach { command ->
            val result = requestNotificationUseCase.request(command)
            if (result.duplicated) duplicated += 1 else accepted += 1
        }

        // 3. 완료 기록.
        val completed = routingJobPersistencePort.save(
            job.complete(
                targetCount = commands.size,
                acceptedCount = accepted,
                duplicatedCount = duplicated,
                now = clock.instant(),
            )
        )
        return result(completed, RouteNotificationOutcome.ROUTED)
    }

    private suspend fun findOrStart(eventKey: String, kind: RoutingJobKind, keyword: String): RoutingJob {
        routingJobPersistencePort.findByEventKey(eventKey)?.let { return it }

        return try {
            routingJobPersistencePort.insert(RoutingJob.start(eventKey, kind, keyword, clock.instant()))
        } catch (exception: RoutingJobConflictException) {
            // 같은 이벤트를 동시에 받은 다른 인스턴스가 먼저 만들었다. 그 job으로 진행한다.
            routingJobPersistencePort.findByEventKey(eventKey)
                ?: throw IllegalStateException("routing job conflict but not found. eventKey=$eventKey", exception)
        }
    }

    private fun result(job: RoutingJob, outcome: RouteNotificationOutcome): RouteNotificationResult {
        return RouteNotificationResult(
            jobId = job.id,
            outcome = outcome,
            targetCount = job.targetCount,
            acceptedCount = job.acceptedCount,
            duplicatedCount = job.duplicatedCount,
        )
    }
}
