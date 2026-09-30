package me.rgunny.kachi.notification.application.service

import java.time.Clock
import java.time.Instant
import me.rgunny.kachi.notification.application.port.inbound.dispatch.DispatchNotificationUseCase
import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.DispatchFailureClassification
import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.DispatchNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.DispatchNotificationResult
import me.rgunny.kachi.notification.application.port.outbound.idempotency.NotificationDeduplicationPort
import me.rgunny.kachi.notification.application.port.outbound.idempotency.NotificationIdempotencyKeyPort
import me.rgunny.kachi.notification.application.port.outbound.notification.NotificationDispatchPersistencePort
import me.rgunny.kachi.notification.application.port.outbound.notification.NotificationPersistencePort
import me.rgunny.kachi.notification.application.port.outbound.recipient.RecipientResolverPort
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.AvailableRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.UnavailableRecipient
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationResult
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.exception.NotificationNotFoundException
import me.rgunny.kachi.notification.exception.dispatch.DispatchNotReadyException
import me.rgunny.kachi.notification.exception.recipient.RecipientResolveException
import me.rgunny.kachi.notification.exception.sender.NonRetryableSendException
import me.rgunny.kachi.notification.exception.sender.RetryableSendException
import me.rgunny.kachi.notification.domain.retry.FailureCategory
import me.rgunny.kachi.notification.domain.retry.RetryDecision
import me.rgunny.kachi.notification.domain.retry.RetryFailure

/**
 * notification.dispatch 발송 실행 application service.
 *
 * claim한 알림의 `(recipientId, channel)`을 수신 주소로 바꾼 뒤 sender에 넘긴다.
 * 주소가 없으면 발송하지 않고 SUPPRESSED로 끝내고, 조회가 실패하면 발송 실패와 같은 재시도 분류를 탄다.
 *
 * 발송 직전에 `notification:sent:{requestId}` 마커를 선점한다.
 * vendor 호출 뒤 결과를 저장하기 전에 worker가 죽어 stale 회수로 RETRY_WAIT가 된 알림이 다시 나가는 것을 막는 마지막 층이다.
 * 선점에 실패하면 SUPPRESSED로 끝낸다.
 */
class DispatchNotificationService(
    private val notificationPersistencePort: NotificationPersistencePort,
    private val dispatchPersistencePort: NotificationDispatchPersistencePort,
    private val deduplicationPort: NotificationDeduplicationPort,
    private val recipientResolverPort: RecipientResolverPort,
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
        // REQUESTED면 아직 dispatch 발행 완료 반영 전이다. PUBLISHED·RETRY_WAIT면 claim 가능한 상태인데 claim이 실패한 것이므로
        // 발행 완료 반영 transaction이나 같은 행의 다른 claim과 경합한 것이다. 어느 쪽이든 재시도 신호를 내고 다음 시도의 CAS가 가른다.
        // 종착/처리 중 상태면 skip한다. 경합을 중복으로 보고 ack하면 알림이 발행 완료 상태에 영구히 남는다.
        if (claimedNotification == null) {
            val notification = notificationPersistencePort.findById(command.notificationId)
                ?: throw NotificationNotFoundException(command.notificationId)

            if (notification.status in RETRY_ON_CLAIM_MISS) {
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

        // sent 마커를 잡은 뒤 RETRY_WAIT나 예상 밖 예외로 빠져나가면 풀어야 다음 시도가 막히지 않는다.
        var sentGuardKey: String? = null

        val sendResult = try {
            // 6. claim한 알림의 (recipientId, channel)을 수신 주소로 바꾼다.
            // 주소가 없으면 sender와 idempotency key를 건드리지 않고 SUPPRESSED로 끝낸다.
            val address = when (val resolved = recipientResolverPort.resolve(
                recipientId = claimedNotification.recipientId,
                channel = claimedNotification.channel,
            )) {
                is AvailableRecipient -> resolved.address
                is UnavailableRecipient -> return completeAsSuppressed(
                    claimed = claimedNotification,
                    reason = "recipient unavailable: ${resolved.reason}",
                    now = now,
                )
            }

            // 7. claim에 성공한 알림에 대해 vendor idempotency key를 조회하거나 새로 만든다.
            val idempotencyKey = idempotencyKeyPort.getOrCreate(
                notificationId = claimedNotification.id,
                ttl = policy.idempotencyKeyTtl,
            )

            // 8. 발송 직전에 requestId 단위 sent 마커를 선점한다. 이미 있으면 vendor 호출까지 간 시도가 있었던 것이다.
            val guardKey = sentGuardKey(command.requestId)
            if (!deduplicationPort.acquire(guardKey, policy.idempotencyKeyTtl)) {
                return completeAsSuppressed(
                    claimed = claimedNotification,
                    reason = ALREADY_SENT_REASON,
                    now = now,
                )
            }
            sentGuardKey = guardKey

            // 9. channel에 맞는 sender를 선택하고 조회한 주소로 발송한다.
            val sender = senderRouter.route(claimedNotification.channel)
            sender.send(
                SendNotificationCommand(
                    notificationId = claimedNotification.id,
                    channel = claimedNotification.channel,
                    address = address,
                    message = command.message,
                    idempotencyKey = idempotencyKey,
                )
            )
        } catch (e: RecipientResolveException) {
            // 주소 조회 실패는 발송 실패와 같은 재시도 분류를 탄다. sender는 부르지 않았다.
            return completeAsRetryableFailure(
                claimed = claimedNotification,
                failure = e.failure,
                dedupeKey = dedupeKey,
                now = now,
                dispatchAttempted = false,
            )
        } catch (e: RetryableSendException) {
            toRetryableResult(e)
        } catch (e: NonRetryableSendException) {
            SendNotificationResult.PermanentFailure(e.failure)
        } catch (e: Exception) {
            // TODO: PROCESSING 상태가 이미 확정된 뒤의 장애까지 내부 회수하려면 stale PROCESSING recovery use case를 별도로 둔다.
            sentGuardKey?.let { deduplicationPort.release(it) }
            deduplicationPort.release(dedupeKey)
            throw e
        }

        // 10. sender가 명시적인 결과를 반환하면 그 결과를 기준으로 알림 상태를 확정한다.
        // vendor HTTP/Redis는 Mongo rollback 대상이 아니므로, 외부 호출 이후 DB finalize는 별도 저장 경계로 분리한다.
        return when (sendResult) {
            is SendNotificationResult.Success -> completeAsSent(
                claimed = claimedNotification,
                now = now,
            )
            is SendNotificationResult.RateLimited -> completeAsRetryableFailure(
                claimed = claimedNotification,
                failure = sendResult.failure,
                dedupeKey = dedupeKey,
                sentGuardKey = sentGuardKey,
                now = now,
            )
            is SendNotificationResult.TransientFailure -> completeAsRetryableFailure(
                claimed = claimedNotification,
                failure = sendResult.failure,
                dedupeKey = dedupeKey,
                sentGuardKey = sentGuardKey,
                now = now,
            )
            is SendNotificationResult.PermanentFailure -> completeAsDead(
                claimed = claimedNotification,
                failure = sendResult.failure,
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

    private fun sentGuardKey(requestId: String): String {
        return "notification:sent:$requestId"
    }

    private suspend fun completeAsSent(
        claimed: Notification,
        now: Instant,
    ): DispatchNotificationResult {
        // 1. vendor 발송 성공이 확인된 뒤에만 domain 상태를 SENT로 확정한다.
        val sent = claimed.markSent(now)

        // 2. 외부 API 호출은 rollback할 수 없으므로, 발송 이후 DB finalize를 별도 저장 경계로 수행한다.
        // 이때 claim fencing 조건이 맞지 않으면 늦은 worker 결과로 보고 현재 DB 상태를 따른다.
        val savedNotification = saveFinalizedIfClaimMatches(claimed, sent)
            ?: return staleFinalizeResult(claimed, now)

        // 3. Kafka listener는 이 결과를 보고 offset ack 여부를 결정한다.
        return DispatchNotificationResult(
            notificationId = savedNotification.id,
            status = savedNotification.status,
            duplicated = false,
            dispatchAttempted = true,
            dispatchCompletedAt = now,
        )
    }

    private suspend fun completeAsSuppressed(
        claimed: Notification,
        reason: String,
        now: Instant,
    ): DispatchNotificationResult {
        // 1. 보낼 곳이 없으므로 발송 시도 없이 SUPPRESSED로 종착시킨다. attempts는 올리지 않는다.
        val suppressed = claimed.markSuppressed(now, reason)

        // 2. 다른 종착과 같은 claim fencing 저장 경계를 탄다.
        val savedNotification = saveFinalizedIfClaimMatches(claimed, suppressed)
            ?: return staleFinalizeResult(claimed, now, dispatchAttempted = false)

        // 3. terminal이므로 listener는 ack한다. dispatch dedupe 마커는 TTL로 사라진다.
        return DispatchNotificationResult(
            notificationId = savedNotification.id,
            status = savedNotification.status,
            duplicated = false,
            dispatchAttempted = false,
            dispatchCompletedAt = now,
        )
    }

    private suspend fun completeAsRetryableFailure(
        claimed: Notification,
        failure: RetryFailure,
        dedupeKey: String,
        now: Instant,
        sentGuardKey: String? = null,
        dispatchAttempted: Boolean = true,
    ): DispatchNotificationResult {
        // 1. 이번 발송 시도를 포함한 attempts로 retry/give-up 결정을 계산한다.
        val nextAttempts = claimed.dispatchAttempts + 1
        val decision = policy.retryPolicy.decide(failure, nextAttempts)
        val reason = failure.message

        // 2. 실패 이력을 먼저 남기고, retry 정책 결과에 따라 RETRY_WAIT 또는 DEAD로 확정한다.
        val failed = claimed.markFailed(now, reason)
        val (finalized, failureClassification) = when (decision) {
            is RetryDecision.Retry -> failed.markRetryWait(now, reason) to DispatchFailureClassification.RETRYABLE
            is RetryDecision.GiveUp -> failed.markDead(now, reason) to DispatchFailureClassification.NON_RETRYABLE
        }

        // 3. 계산된 최종 상태를 DB에 먼저 저장한다. 저장 전 dedupe를 풀면 다음 메시지가 낡은 상태를 볼 수 있다.
        val savedNotification = saveFinalizedIfClaimMatches(claimed, finalized)
            ?: return staleFinalizeResult(claimed, now, dispatchAttempted)
        if (failureClassification == DispatchFailureClassification.RETRYABLE) {
            // 4. 재시도 가능한 실패는 RETRY_WAIT 저장 후에만 sent 마커와 dedupe marker를 해제해 다음 dispatch 메시지를 허용한다.
            sentGuardKey?.let { deduplicationPort.release(it) }
            deduplicationPort.release(dedupeKey)
        }

        // 5. listener는 RETRYABLE classification을 보고 Kafka retry/DLT 흐름으로 연결한다.
        return DispatchNotificationResult(
            notificationId = savedNotification.id,
            status = savedNotification.status,
            duplicated = false,
            dispatchAttempted = dispatchAttempted,
            dispatchCompletedAt = now,
            failureClassification = failureClassification,
            failure = failure,
        )
    }

    private suspend fun completeAsDead(
        claimed: Notification,
        failure: RetryFailure,
        now: Instant,
    ): DispatchNotificationResult {
        // 1. 재시도하지 않을 실패도 실패 이력을 먼저 남긴다.
        val reason = failure.message
        val failed = claimed.markFailed(now, reason)

        // 2. 이후 DEAD로 종착시켜 같은 dispatch 메시지가 다시 발송을 시도하지 않게 한다.
        val dead = failed.markDead(now, reason)

        // 3. DEAD 저장이 성공하면 listener가 offset을 ack해 Kafka 재처리를 끝낸다.
        val savedNotification = saveFinalizedIfClaimMatches(claimed, dead)
            ?: return staleFinalizeResult(claimed, now)
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
        dispatchAttempted: Boolean = true,
    ): DispatchNotificationResult {
        val current = notificationPersistencePort.findById(notification.id)
            ?: throw NotificationNotFoundException(notification.id)

        return DispatchNotificationResult(
            notificationId = current.id,
            status = current.status,
            duplicated = true,
            dispatchAttempted = dispatchAttempted,
            dispatchCompletedAt = now,
            failureClassification = DispatchFailureClassification.NONE,
        )
    }

    private companion object {
        /** claim이 실패했어도 이 상태면 다음 시도가 다시 claim할 수 있다. */
        val RETRY_ON_CLAIM_MISS = setOf(
            NotificationStatus.REQUESTED,
            NotificationStatus.PUBLISHED,
            NotificationStatus.RETRY_WAIT,
        )
        const val ALREADY_SENT_REASON = "already sent"
    }

    /**
     * 전이 결과를 저장하되, claim 조건은 전이 전 인스턴스가 그대로 들고 있는 값을 쓴다.
     */
    private suspend fun saveFinalizedIfClaimMatches(
        claimed: Notification,
        finalized: Notification,
    ): Notification? {
        return dispatchPersistencePort.saveFinalizedIfProcessingClaimMatches(
            notification = finalized,
            expectedClaimedAt = claimed.claimedAt!!,
            expectedClaimedBy = claimed.claimedBy!!,
        )
    }
}
