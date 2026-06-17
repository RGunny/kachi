package me.rgunny.kachi.notification.service.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.notification")
data class NotificationServiceProperties(
    val request: Request = Request(),
    val dispatch: Dispatch = Dispatch(),
    val outbox: Outbox = Outbox(),
){

    data class Request(
        val topic: String = "notification.requested",
        val groupId: String = "notification-service",
        val dedupeTtl: Duration = Duration.ofHours(24),
        val dlt: Dlt = Dlt(),
        val retry: Retry = Retry(),
    ) {

        data class Dlt(
            val topic: String = "notification.requested.dlt",
        )

        data class Retry(
            val maxAttempts: Long = 3,
            val backoff: Duration = Duration.ofSeconds(1),
        )
    }

    data class Dispatch(
        val topic: String = "notification.dispatch"
    )

    data class Outbox(
        val publisherId: String = "notification-service-local",
        val batchSize: Int = 100,
        val pollInterval: Duration = Duration.ofSeconds(1),
        val publishingVisibilityTimeout: Duration = Duration.ofSeconds(30),
        val retry: Retry = Retry(),
        val scheduler: Scheduler = Scheduler()
    ) {

        data class Retry(
            val maxAttempts: Int = 3,
            val baseDelay: Duration = Duration.ofSeconds(1),
            val maxDelay: Duration = Duration.ofMinutes(1)
        )

        data class Scheduler(
            val enabled: Boolean = true,
        )

    }

}
