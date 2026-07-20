package me.rgunny.kachi.notification.worker.config

import me.rgunny.kachi.notification.application.port.inbound.DispatchNotificationUseCase
import me.rgunny.kachi.notification.application.port.inbound.PersistNotificationDltMessageUseCase
import me.rgunny.kachi.notification.application.port.inbound.RecoverStaleProcessingDispatchUseCase
import me.rgunny.kachi.notification.application.port.outbound.NotificationDeduplicationPort
import me.rgunny.kachi.notification.application.port.outbound.NotificationDispatchPersistencePort
import me.rgunny.kachi.notification.application.port.outbound.NotificationDltMessagePersistencePort
import me.rgunny.kachi.notification.application.port.outbound.NotificationIdempotencyKeyPort
import me.rgunny.kachi.notification.application.port.outbound.NotificationPersistencePort
import me.rgunny.kachi.notification.application.port.outbound.NotificationSender
import me.rgunny.kachi.notification.application.service.DispatchNotificationPolicy
import me.rgunny.kachi.notification.application.service.DispatchNotificationService
import me.rgunny.kachi.notification.application.service.NotificationSenderRouter
import me.rgunny.kachi.notification.application.service.PersistNotificationDltMessageService
import me.rgunny.kachi.notification.application.service.RecoverStaleProcessingDispatchService
import me.rgunny.kachi.notification.retry.RetryPolicy
import me.rgunny.kachi.notification.worker.adapter.outbound.monitoring.NotificationWorkerMetrics
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.MeteredNotificationSender
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * notification-core dispatch use case를 worker 런타임에 조립하는 composition root.
 *
 * notification-core는 Spring annotation 없이 POJO로 유지하므로, worker가 필요한 port 구현체와 정책을 여기서 주입한다.
 * 실제 Mongo/Redis/Sender adapter는 worker adapter 패키지에서 구현하고, 이 config는 조립 책임만 가진다.
 */
@Configuration
@EnableConfigurationProperties(
    NotificationWorkerProperties::class,
    NotificationDispatchProperties::class,
    NotificationSenderProperties::class,
)
class NotificationWorkerCoreConfig {

    /**
     * 상태 전이 시각을 UTC 기준으로 통일한다.
     */
    @Bean
    fun clock(): Clock {
        return Clock.systemUTC()
    }

    /**
     * sender 실패에 대한 core retry 판단 정책.
     *
     * Kafka listener retry와 같은 maxAttempts/backoff 설정을 사용해야
     * Notification 상태(RETRY_WAIT/DEAD)와 Kafka retry/DLT 이동 시점이 서로 어긋나지 않는다.
     */
    @Bean
    fun dispatchRetryPolicy(properties: NotificationDispatchProperties): RetryPolicy {
        return RetryPolicy(
            maxAttempts = properties.retry.maxAttempts.toInt(),
            baseDelay = properties.retry.backoff,
            maxDelay = properties.retry.backoff,
        )
    }

    /**
     * dispatch use case가 사용하는 worker 정책.
     *
     * dedupeTtl은 worker 중복 consume 방지용이고, idempotencyKeyTtl은 vendor 중복 발송 방지용이다.
     * 두 TTL은 보호하는 경계가 다르므로 같은 값으로 묶지 않는다.
     */
    @Bean
    fun dispatchNotificationPolicy(
        workerProperties: NotificationWorkerProperties,
        dispatchProperties: NotificationDispatchProperties,
        dispatchRetryPolicy: RetryPolicy,
    ): DispatchNotificationPolicy {
        return DispatchNotificationPolicy(
            workerId = workerProperties.workerId,
            dedupeTtl = dispatchProperties.dedupeTtl,
            idempotencyKeyTtl = dispatchProperties.idempotencyKeyTtl,
            retryPolicy = dispatchRetryPolicy,
            processingVisibilityTimeout = dispatchProperties.processingVisibilityTimeout,
            recoveryBatchSize = dispatchProperties.recovery.batchSize,
        )
    }

    /**
     * 채널별 sender 구현체를 core router에 전달한다.
     * Spring이 주입한 sender adapter를 metric decorator로 감싼 뒤 router에 전달한다.
     */
    @Bean
    fun notificationSenderRouter(
        senders: List<NotificationSender>,
        metrics: NotificationWorkerMetrics,
    ): NotificationSenderRouter {
        return NotificationSenderRouter(
            senders.map { sender ->
                MeteredNotificationSender(
                    delegate = sender,
                    metrics = metrics,
                )
            }
        )
    }

    /**
     * notification.dispatch 메시지를 실제 발송 use case에 연결한다.
     */
    @Bean
    fun dispatchNotificationUseCase(
        notificationPersistencePort: NotificationPersistencePort,
        dispatchPersistencePort: NotificationDispatchPersistencePort,
        deduplicationPort: NotificationDeduplicationPort,
        idempotencyKeyPort: NotificationIdempotencyKeyPort,
        senderRouter: NotificationSenderRouter,
        dispatchNotificationPolicy: DispatchNotificationPolicy,
        clock: Clock,
    ): DispatchNotificationUseCase {
        return DispatchNotificationService(
            notificationPersistencePort = notificationPersistencePort,
            dispatchPersistencePort = dispatchPersistencePort,
            deduplicationPort = deduplicationPort,
            idempotencyKeyPort = idempotencyKeyPort,
            senderRouter = senderRouter,
            policy = dispatchNotificationPolicy,
            clock = clock,
        )
    }

    @Bean
    fun recoverStaleProcessingDispatchUseCase(
        notificationPersistencePort: NotificationPersistencePort,
        dispatchPersistencePort: NotificationDispatchPersistencePort,
        deduplicationPort: NotificationDeduplicationPort,
        dispatchNotificationPolicy: DispatchNotificationPolicy,
        clock: Clock,
    ): RecoverStaleProcessingDispatchUseCase {
        return RecoverStaleProcessingDispatchService(
            notificationPersistencePort = notificationPersistencePort,
            dispatchPersistencePort = dispatchPersistencePort,
            deduplicationPort = deduplicationPort,
            policy = dispatchNotificationPolicy,
            clock = clock,
        )
    }

    @Bean
    fun persistNotificationDltMessageUseCase(
        dltMessagePersistencePort: NotificationDltMessagePersistencePort,
    ): PersistNotificationDltMessageUseCase {
        return PersistNotificationDltMessageService(dltMessagePersistencePort)
    }
}
