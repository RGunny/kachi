package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.dto.DispatchFailureClassification
import me.rgunny.kachi.notification.application.port.dto.DispatchNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.DispatchNotificationResult
import me.rgunny.kachi.notification.application.port.dto.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.SendNotificationResult
import me.rgunny.kachi.notification.application.port.inbound.DispatchNotificationUseCase
import me.rgunny.kachi.notification.application.port.outbound.NotificationDeduplicationPort
import me.rgunny.kachi.notification.application.port.outbound.NotificationDispatchPersistencePort
import me.rgunny.kachi.notification.application.port.outbound.NotificationIdempotencyKeyPort
import me.rgunny.kachi.notification.application.port.outbound.NotificationPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.exception.dispatch.DispatchNotReadyException
import me.rgunny.kachi.notification.exception.sender.NonRetryableSendException
import me.rgunny.kachi.notification.exception.NotificationNotFoundException
import me.rgunny.kachi.notification.exception.sender.RetryableSendException
import me.rgunny.kachi.notification.retry.FailureCategory
import me.rgunny.kachi.notification.retry.RetryDecision
import me.rgunny.kachi.notification.retry.RetryFailure
import java.time.Clock
import java.time.Instant

/**
 * notification.dispatch 발송 실행 application service.
 */
class DispatchNotificationService(
    private val notificationPersistencePort: NotificationPersistencePort,
    private val dispatchPersistencePort: NotificationDispatchPersistencePort,
    private val deduplicationPort: NotificationDeduplicationPort,
    private val idempotencyKeyPort: NotificationIdempotencyKeyPort,
    private val senderRouter: NotificationSenderRouter,
    private val policy: DispatchNotificationPolicy,
    private val clock: Clock,
) : DispatchNotificationUseCase {

    override suspend fun dispatch(command: DispatchNotificationCommand): DispatchNotificationResult {
        val now = Instant.now(clock)

        // 1. notificationId를 멱등키로 사용해 같은 dispatch 메시지의 중복 실행을 막는다.
        val dedupeKey = dispatchDedupeKey(command.notificationId)

        // 2. 멱등 마커를 1차 가드로 선점하고, 최종 중복 방어는 DB CAS claim에 맡긴다.
        val isDuplicateDispatch = !deduplicationPort.acquire(dedupeKey, policy.dedupeTtl)

        // 3. 이미 선점된 메시지는 처리 중이거나 처리된 것으로 보고 현재 상태를 조회해 중복 결과를 반환한다.
        if (isDuplicateDispatch) {
            val notification = notificationPersistencePort.findById(command.notificationId)
                ?: throw NotificationNotFoundException(command.notificationId)
            return DispatchNotificationResult(
                notificationId = notification.id,
                status = notification.status,
                duplicated = true,
                dispatchAttempted = false,
                dispatchCompletedAt = now,
            )
        }

        // 4. 발송 진입은 PUBLISHED -> PROCESSING claim을 먼저 시도하고, 실패하면 RETRY_WAIT -> PROCESSING claim을 시도한다.
        val claimedNotification =
            notificationPersistencePort.claimFromPublished(
                notificationId = command.notificationId,
                workerId = policy.workerId,
                now = now,
            ) ?: notificationPersistencePort.claimFromRetryWait(
                notificationId = command.notificationId,
                workerId = policy.workerId,
                now = now,
            )

        // 5. 두 claim 모두 실패하면 현재 상태를 다시 조회한다.
        // REQUESTED면 아직 dispatch 발행 완료 반영 전이므로 재시도 신호를 내고, 종착/처리 중 상태면 skip한다.
        if (claimedNotification == null) {
            val notification = notificationPersistencePort.findById(command.notificationId)
                ?: throw NotificationNotFoundException(command.notificationId)

            if (notification.status == NotificationStatus.REQUESTED) {
                deduplicationPort.release(dedupeKey)
                throw DispatchNotReadyException(command.notificationId, notification.status)
            }
            return DispatchNotificationResult(
                notificationId = notification.id,
                status = notification.status,
                duplicated = true,
                dispatchAttempted = false,
                dispatchCompletedAt = now,
            )
        }

        val processingClaim = ProcessingClaim.from(claimedNotification)
        val sendResult = try {
            // 6. claim에 성공한 알림에 대해 vendor idempotency key를 조회하거나 새로 만든다.
            val idempotencyKey = idempotencyKeyPort.getOrCreate(
                notificationId = claimedNotification.id,
                ttl = policy.idempotencyKeyTtl,
            )

            // 7. channel에 맞는 sender를 선택하고 외부 채널로 발송한다.
            val sender = senderRouter.route(claimedNotification.channel)
            sender.send(
                SendNotificationCommand(
                    notificationId = claimedNotification.id,
                    channel = claimedNotification.channel,
                    recipient = claimedNotification.recipient,
                    message = command.message,
                    idempotencyKey = idempotencyKey,
                )
            )
        } catch (e: RetryableSendException) {
            toRetryableResult(e)
        } catch (e: NonRetryableSendException) {
            SendNotificationResult.PermanentFailure(e.failure)
        } catch (e: Exception) {
            // TODO: PROCESSING 상태가 이미 확정된 뒤의 장애까지 내부 회수하려면 stale PROCESSING recovery use case를 별도로 둔다.
            deduplicationPort.release(dedupeKey)
            throw e
        }

        // 8. sender가 명시적인 결과를 반환하면 그 결과를 기준으로 알림 상태를 확정한다.
        // vendor HTTP/Redis는 Mongo rollback 대상이 아니므로, 외부 호출 이후 DB finalize는 별도 저장 경계로 분리한다.
        return when (sendResult) {
            is SendNotificationResult.Success -> completeAsSent(
                notification = claimedNotification,
                processingClaim = processingClaim,
                now = now,
            )
            is SendNotificationResult.RateLimited -> completeAsRetryableFailure(
                notification = claimedNotification,
                failure = sendResult.failure,
                dedupeKey = dedupeKey,
                processingClaim = processingClaim,
                now = now,
            )
            is SendNotificationResult.TransientFailure -> completeAsRetryableFailure(
                notification = claimedNotification,
                failure = sendResult.failure,
                dedupeKey = dedupeKey,
                processingClaim = processingClaim,
                now = now,
            )
            is SendNotificationResult.PermanentFailure -> completeAsDead(
                notification = claimedNotification,
                failure = sendResult.failure,
                processingClaim = processingClaim,
                now = now,
            )
        }
    }

    private fun toRetryableResult(exception: RetryableSendException): SendNotificationResult {
        return when (exception.failure.category) {
            FailureCategory.RATE_LIMITED -> SendNotificationResult.RateLimited(exception.failure)
            else -> SendNotificationResult.TransientFailure(exception.failure)
        }
    }

    private fun dispatchDedupeKey(notificationId: NotificationId): String {
        return "notification:dispatch:${notificationId.id}"
    }

    private suspend fun completeAsSent(
        notification: Notification,
        processingClaim: ProcessingClaim,
        now: Instant,
    ): DispatchNotificationResult {
        // 1. vendor 발송 성공이 확인된 뒤에만 domain 상태를 SENT로 확정한다.
        notification.markSent(now)

        // 2. 외부 API 호출은 rollback할 수 없으므로, 발송 이후 DB finalize를 별도 저장 경계로 수행한다.
        // 이때 claim fencing 조건이 맞지 않으면 늦은 worker 결과로 보고 현재 DB 상태를 따른다.
        val savedNotification = dispatchPersistencePort.saveFinalizedIfProcessingClaimMatches(
            notification = notification,
            expectedClaimedAt = processingClaim.claimedAt,
            expectedClaimedBy = processingClaim.claimedBy,
        ) ?: return staleFinalizeResult(notification, now)

        // 3. Kafka listener는 이 결과를 보고 offset ack 여부를 결정한다.
        return DispatchNotificationResult(
            notificationId = savedNotification.id,
            status = savedNotification.status,
            duplicated = false,
            dispatchAttempted = true,
            dispatchCompletedAt = now,
        )
    }

    private suspend fun completeAsRetryableFailure(
        notification: Notification,
        failure: RetryFailure,
        dedupeKey: String,
        processingClaim: ProcessingClaim,
        now: Instant,
    ): DispatchNotificationResult {
        // 1. 이번 발송 시도를 포함한 attempts로 retry/give-up 결정을 계산한다.
        val nextAttempts = notification.dispatchAttempts + 1
        val decision = policy.retryPolicy.decide(failure, nextAttempts)
        val reason = failure.message

        // 2. 실패 이력을 먼저 남기고, retry 정책 결과에 따라 RETRY_WAIT 또는 DEAD로 확정한다.
        notification.markFailed(now, reason)
        val failureClassification = when (decision) {
            is RetryDecision.Retry -> {
                notification.markRetryWait(now, reason)
                DispatchFailureClassification.RETRYABLE
            }
            is RetryDecision.GiveUp -> {
                notification.markDead(now, reason)
                DispatchFailureClassification.NON_RETRYABLE
            }
        }

        // 3. 계산된 최종 상태를 DB에 먼저 저장한다. 저장 전 dedupe를 풀면 다음 메시지가 낡은 상태를 볼 수 있다.
        val savedNotification = dispatchPersistencePort.saveFinalizedIfProcessingClaimMatches(
            notification = notification,
            expectedClaimedAt = processingClaim.claimedAt,
            expectedClaimedBy = processingClaim.claimedBy,
        ) ?: return staleFinalizeResult(notification, now)
        if (failureClassification == DispatchFailureClassification.RETRYABLE) {
            // 4. 재시도 가능한 실패는 RETRY_WAIT 저장 후에만 dedupe marker를 해제해 다음 dispatch 메시지를 허용한다.
            deduplicationPort.release(dedupeKey)
        }

        // 5. listener는 RETRYABLE classification을 보고 Kafka retry/DLT 흐름으로 연결한다.
        return DispatchNotificationResult(
            notificationId = savedNotification.id,
            status = savedNotification.status,
            duplicated = false,
            dispatchAttempted = true,
            dispatchCompletedAt = now,
            failureClassification = failureClassification,
            failure = failure,
        )
    }

    private suspend fun completeAsDead(
        notification: Notification,
        failure: RetryFailure,
        processingClaim: ProcessingClaim,
        now: Instant,
    ): DispatchNotificationResult {
        // 1. 재시도하지 않을 실패도 실패 이력을 먼저 남긴다.
        val reason = failure.message
        notification.markFailed(now, reason)

        // 2. 이후 DEAD로 종착시켜 같은 dispatch 메시지가 다시 발송을 시도하지 않게 한다.
        notification.markDead(now, reason)

        // 3. DEAD 저장이 성공하면 listener가 offset을 ack해 Kafka 재처리를 끝낸다.
        val savedNotification = dispatchPersistencePort.saveFinalizedIfProcessingClaimMatches(
            notification = notification,
            expectedClaimedAt = processingClaim.claimedAt,
            expectedClaimedBy = processingClaim.claimedBy,
        ) ?: return staleFinalizeResult(notification, now)
        return DispatchNotificationResult(
            notificationId = savedNotification.id,
            status = savedNotification.status,
            duplicated = false,
            dispatchAttempted = true,
            dispatchCompletedAt = now,
            failureClassification = DispatchFailureClassification.NON_RETRYABLE,
            failure = failure,
        )
    }

    private suspend fun staleFinalizeResult(
        notification: Notification,
        now: Instant,
    ): DispatchNotificationResult {
        val current = notificationPersistencePort.findById(notification.id)
            ?: throw NotificationNotFoundException(notification.id)

        return DispatchNotificationResult(
            notificationId = current.id,
            status = current.status,
            duplicated = true,
            dispatchAttempted = true,
            dispatchCompletedAt = now,
            failureClassification = DispatchFailureClassification.NONE,
        )
    }

    private data class ProcessingClaim(
        val claimedAt: Instant,
        val claimedBy: String,
    ) {
        companion object {
            fun from(notification: Notification): ProcessingClaim {
                return ProcessingClaim(
                    claimedAt = requireNotNull(notification.claimedAt) {
                        "PROCESSING notification must have claimedAt"
                    },
                    claimedBy = requireNotNull(notification.claimedBy) {
                        "PROCESSING notification must have claimedBy"
                    },
                )
            }
        }
    }
}
