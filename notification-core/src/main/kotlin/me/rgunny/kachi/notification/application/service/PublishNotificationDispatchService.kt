package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.dto.PublishNotificationDispatchResult
import me.rgunny.kachi.notification.application.port.inbound.PublishNotificationDispatchUseCase
import me.rgunny.kachi.notification.application.port.outbound.NotificationDispatchPublisher
import me.rgunny.kachi.notification.application.port.outbound.NotificationOutboxPersistencePort
import me.rgunny.kachi.notification.application.port.outbound.NotificationPublishPersistencePort
import java.time.Clock
import java.time.Instant

/**
 * Outbox 기반 notification.dispatch 발행 application service.
 * PENDING outbox를 claim해 notification.dispatch로 발행하고 발행 결과를 상태에 반영한다.
 */
class PublishNotificationDispatchService(
    private val outboxPersistencePort: NotificationOutboxPersistencePort,
    private val publishPersistencePort: NotificationPublishPersistencePort,
    private val dispatchPublisher: NotificationDispatchPublisher,
    private val policy: OutboxPublishPolicy,
    private val clock: Clock,
) : PublishNotificationDispatchUseCase {

    override suspend fun publishPending(): PublishNotificationDispatchResult {
        val now = Instant.now(clock)

        // 1. 발행 가능한 PENDING outbox와 오래 잡혀 있던 PUBLISHING outbox를 같은 주기에 처리한다.
        val publishableOutboxes = outboxPersistencePort.findPublishable(now, policy.batchSize)
        val staleThreshold = now.minus(policy.publishingVisibilityTimeout)
        val staleOutboxes = outboxPersistencePort.findStalePublishing(staleThreshold, policy.batchSize)

        // 2. timeout된 PUBLISHING은 이전 publisher가 중단된 것으로 보고 실패 처리해 재시도 대상으로 돌린다.
        var failed = 0
        staleOutboxes.forEach { outbox ->
            outbox.recordFailure(PUBLISHING_TIMEOUT_REASON, policy.retryPolicy, now)
            publishPersistencePort.savePublishFailed(outbox, now, PUBLISHING_TIMEOUT_REASON)
            failed += 1
        }

        var processed = 0
        var published = 0

        // 3. PENDING outbox는 저장소 CAS로 claim한 publisher만 발행한다.
        publishableOutboxes.forEach { outbox ->
            val claimedOutbox = outboxPersistencePort.claimPublishing(
                outboxId = outbox.id,
                claimedBy = policy.publisherId,
                now = now,
            ) ?: return@forEach

            processed += 1

            // 4. claim에 성공한 outbox payload를 notification.dispatch topic으로 발행한다.
            val publishFailure = runCatching {
                dispatchPublisher.publish(claimedOutbox)
            }.exceptionOrNull()

            if (publishFailure != null) {
                // 6. Kafka publish 실패는 retry 가능한 DB 상태로만 보정한다.
                // 외부 side effect와 DB rollback은 묶을 수 없으므로, 실패 결과 반영만 Mongo transaction으로 확정한다.
                val reason = publishFailure.message?.takeIf { it.isNotBlank() }
                    ?: publishFailure::class.simpleName
                    ?: PUBLISH_FAILED
                claimedOutbox.recordFailure(reason, policy.retryPolicy, now)
                publishPersistencePort.savePublishFailed(claimedOutbox, now, reason)

                failed += 1
                return@forEach
            }

            // 5. broker 발행이 끝난 뒤 outbox와 notification의 발행 완료 상태를 같은 DB transaction으로 맞춘다.
            claimedOutbox.markPublished(now)
            publishPersistencePort.savePublished(claimedOutbox, now)

            published += 1
        }

        // 7. scheduler/admin은 이 결과로 로그와 메트릭만 남긴다.
        return PublishNotificationDispatchResult(
            processed = processed + staleOutboxes.size,
            published = published,
            failed = failed,
            publishTickCompletedAt = now,
        )
    }

    private companion object {
        const val PUBLISHING_TIMEOUT_REASON = "publishing-timeout"
        const val PUBLISH_FAILED = "publish-failed"
    }
}
