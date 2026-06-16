package me.rgunny.kachi.notification.service.config

import me.rgunny.kachi.notification.application.port.inbound.PublishNotificationDispatchUseCase
import me.rgunny.kachi.notification.application.port.inbound.RequestNotificationUseCase
import me.rgunny.kachi.notification.application.port.outbound.NotificationDeduplicationPort
import me.rgunny.kachi.notification.application.port.outbound.NotificationDispatchPublisher
import me.rgunny.kachi.notification.application.port.outbound.NotificationEventSerializer
import me.rgunny.kachi.notification.application.port.outbound.NotificationOutboxPersistencePort
import me.rgunny.kachi.notification.application.port.outbound.NotificationPersistencePort
import me.rgunny.kachi.notification.application.service.OutboxPublishPolicy
import me.rgunny.kachi.notification.application.service.PublishNotificationDispatchService
import me.rgunny.kachi.notification.application.service.RequestNotificationPolicy
import me.rgunny.kachi.notification.application.service.RequestNotificationService
import me.rgunny.kachi.notification.retry.RetryPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(NotificationServiceProperties::class)
class NotificationCoreConfig {

    @Bean
    fun clock(): Clock {
        return Clock.systemUTC()
    }

    @Bean
    fun requestNotificationPolicy(
        properties: NotificationServiceProperties
    ): RequestNotificationPolicy {
        return RequestNotificationPolicy(
            dedupeTtl = properties.request.dedupeTtl,
            dispatchTopic = properties.dispatch.topic
        )
    }

    @Bean
    fun outboxRetryPolicy(
        properties: NotificationServiceProperties,
    ): RetryPolicy {
        return RetryPolicy(
            maxAttempts = properties.outbox.retry.maxAttempts,
            baseDelay = properties.outbox.retry.baseDelay,
            maxDelay = properties.outbox.retry.maxDelay,
        )
    }

    @Bean
    fun outboxPublishPolicy(
        properties: NotificationServiceProperties,
        outboxRetryPolicy: RetryPolicy,
    ): OutboxPublishPolicy {
        return OutboxPublishPolicy(
            batchSize = properties.outbox.batchSize,
            publisherId = properties.outbox.publisherId,
            retryPolicy = outboxRetryPolicy,
            publishingVisibilityTimeout = properties.outbox.publishingVisibilityTimeout,
        )
    }

    @Bean
    fun requestNotificationUseCase(
        notificationPersistencePort: NotificationPersistencePort,
        outboxPersistencePort: NotificationOutboxPersistencePort,
        deduplicationPort: NotificationDeduplicationPort,
        eventSerializer: NotificationEventSerializer,
        requestNotificationPolicy: RequestNotificationPolicy,
        clock: Clock,
    ): RequestNotificationUseCase {
        return RequestNotificationService(
            notificationPersistencePort = notificationPersistencePort,
            outboxPersistencePort = outboxPersistencePort,
            deduplicationPort = deduplicationPort,
            eventSerializer = eventSerializer,
            policy = requestNotificationPolicy,
            clock = clock,
        )
    }

    @Bean
    fun publishNotificationDispatchUseCase(
        notificationPersistencePort: NotificationPersistencePort,
        outboxPersistencePort: NotificationOutboxPersistencePort,
        dispatchPublisher: NotificationDispatchPublisher,
        outboxPublishPolicy: OutboxPublishPolicy,
        clock: Clock,
    ): PublishNotificationDispatchUseCase {
        return PublishNotificationDispatchService(
            notificationPersistencePort = notificationPersistencePort,
            outboxPersistencePort = outboxPersistencePort,
            dispatchPublisher = dispatchPublisher,
            policy = outboxPublishPolicy,
            clock = clock,
        )
    }

}
