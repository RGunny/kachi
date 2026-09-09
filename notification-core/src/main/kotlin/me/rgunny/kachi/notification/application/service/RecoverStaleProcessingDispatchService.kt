package me.rgunny.kachi.notification.application.service

import java.time.Clock
import java.time.Instant
import me.rgunny.kachi.notification.application.port.inbound.dispatch.RecoverStaleProcessingDispatchUseCase
import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.RecoverStaleProcessingDispatchResult
import me.rgunny.kachi.notification.application.port.outbound.idempotency.NotificationDeduplicationPort
import me.rgunny.kachi.notification.application.port.outbound.notification.NotificationDispatchPersistencePort
import me.rgunny.kachi.notification.application.port.outbound.notification.NotificationPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.retry.RetryDecision
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryFailureCode

/**
 * PROCESSING 상태로 멈춘 dispatch 회수 application service.
 *
 * PROCESSING은 이미 worker가 DB claim에 성공했다는 뜻이다.
 * 이후 Redis idempotency key 생성, vendor API 호출, DB finalize 사이에서 worker가 중단되면 MongoDB rollback으로 되돌릴 수 없다.
 * 이 유스케이스는 visibility timeout을 넘긴 PROCESSING 알림을 retry 대기 또는 DEAD 상태로 회수한다.
 */
class RecoverStaleProcessingDispatchService(
    private val notificationPersistencePort: NotificationPersistencePort,
    private val dispatchPersistencePort: NotificationDispatchPersistencePort,
    private val deduplicationPort: NotificationDeduplicationPort,
    private val policy: DispatchNotificationPolicy,
    private val clock: Clock,
) : RecoverStaleProcessingDispatchUseCase {

    /**
     * visibility timeout을 넘긴 PROCESSING 알림 batch를 조회하고 각 알림의 회수 결과를 집계한다.
     */
    override suspend fun recoverStaleProcessing(): RecoverStaleProcessingDispatchResult {
        val now = Instant.now(clock)

        // 1. visibility timeout을 기준으로 오래된 PROCESSING claim만 회수 대상으로 잡는다.
        val threshold = now.minus(policy.processingVisibilityTimeout)

        // 2. 한 tick에서 너무 많은 document를 만지지 않도록 batch size로 제한한다.
        val staleNotifications = notificationPersistencePort.findStaleProcessing(
            threshold = threshold,
            batchSize = policy.recoveryBatchSize,
        )

        var retryWait = 0
        var dead = 0
        var skipped = 0

        // 3. 각 notification은 독립적으로 retry 대기 또는 DEAD로 finalize한다.
        staleNotifications.forEach { notification ->
            val recoveredStatus = recover(notification, now)
            when (recoveredStatus) {
                RecoveredStatus.RETRY_WAIT -> retryWait += 1
                RecoveredStatus.DEAD -> dead += 1
                RecoveredStatus.SKIPPED -> skipped += 1
            }
        }

        return RecoverStaleProcessingDispatchResult(
            staleProcessingFound = staleNotifications.size,
            staleProcessingRecovered = retryWait + dead,
            recoveredToRetryWait = retryWait,
            recoveredToDead = dead,
            staleProcessingSkipped = skipped,
            recoveryTickCompletedAt = now,
        )
    }

    /**
     * 단일 PROCESSING 알림을 retry 정책에 따라 RETRY_WAIT 또는 DEAD로 최종 회수한다.
     */
    private suspend fun recover(
        notification: Notification,
        now: Instant,
    ): RecoveredStatus {
        // 1. PROCESSING timeout은 worker가 vendor 호출 또는 DB finalize 사이에서 중단된 것으로 분류한다.
        val failure = RetryFailure.of(RetryFailureCode.DISPATCH_PROCESSING_TIMEOUT)

        // 2. 이미 PROCESSING으로 들어간 시도까지 포함해 retry 정책을 계산한다.
        val nextAttempts = notification.dispatchAttempts + 1
        val reason = failure.message

        // 3. timeout 실패 이력을 남기고 retry 가능 여부에 따라 상태를 확정한다.
        val failed = notification.markFailed(now, reason)
        val (recovered, status) = when (policy.retryPolicy.decide(failure, nextAttempts)) {
            is RetryDecision.Retry -> failed.markRetryWait(now, reason) to RecoveredStatus.RETRY_WAIT
            is RetryDecision.GiveUp -> failed.markDead(now, reason) to RecoveredStatus.DEAD
        }

        // 4. 회수 상태가 DB에 저장되기 전에는 dedupe marker를 풀지 않는다.
        // claim 조건은 전이 전 인스턴스가 그대로 들고 있다.
        val savedNotification = dispatchPersistencePort.saveFinalizedIfProcessingClaimMatches(
            notification = recovered,
            expectedClaimedAt = notification.claimedAt!!,
            expectedClaimedBy = notification.claimedBy!!,
        ) ?: return RecoveredStatus.SKIPPED

        if (status == RecoveredStatus.RETRY_WAIT) {
            // 5. RETRY_WAIT 저장 후 marker를 해제해야 다음 dispatch 메시지가 다시 claim을 시도할 수 있다.
            deduplicationPort.release(dispatchDedupeKey(savedNotification))
        }

        return status
    }

    private fun dispatchDedupeKey(notification: Notification): String {
        return "notification:dispatch:${notification.id.id}"
    }

    private enum class RecoveredStatus {
        RETRY_WAIT,
        DEAD,
        SKIPPED,
    }
}
